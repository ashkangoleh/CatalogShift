/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.polaris.tools.sync.polaris.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.polaris.core.admin.model.Catalog;
import org.apache.polaris.core.admin.model.CatalogRole;
import org.apache.polaris.core.admin.model.GrantResource;
import org.apache.polaris.core.admin.model.Principal;
import org.apache.polaris.core.admin.model.PrincipalRole;
import org.apache.polaris.tools.sync.polaris.planning.plan.SynchronizationPlan;
import org.eclipse.collections.impl.block.procedure.CollectIfProcedure;

/** Planner that checks for modifications and plans to skip entities that have not been modified. */
public class ModificationAwarePlanner implements SynchronizationPlanner {

  private static final String CREATE_TIMESTAMP = "createTimestamp";

  private static final String LAST_UPDATE_TIMESTAMP = "lastUpdateTimestamp";

  private static final String ENTITY_VERSION = "entityVersion";

  private static final List<String> DEFAULT_KEYS_TO_IGNORE =
      List.of(CREATE_TIMESTAMP, LAST_UPDATE_TIMESTAMP, ENTITY_VERSION);

  private static final List<String> CATALOG_KEYS_TO_IGNORE =
      List.of(
          // defaults
          CREATE_TIMESTAMP,
          LAST_UPDATE_TIMESTAMP,
          ENTITY_VERSION,

          // For certain storageConfigInfo fields, depending on the credentials Polaris was set up
          // with
          // to access the storage, some fields will always be different across the source and the
          // target.
          // For example, for S3 my source and target Polaris instances may be set up with different
          // AWS users,
          // each of which assumes the same role to access the storage

          // S3
          "storageConfigInfo.userArn",
          "storageConfigInfo.externalId",

          // AZURE
          "storageConfigInfo.consentUrl",
          "storageConfigInfo.multiTenantAppName",

          // GCP
          "storageConfigInfo.gcsServiceAccount");

  private static final String CLIENT_ID = "clientId";

  private static final String CLIENT_SECRET = "clientSecret";

  private static final List<String> PRINCIPAL_KEYS_TO_IGNORE = List.of(
          CREATE_TIMESTAMP,
          LAST_UPDATE_TIMESTAMP,
          ENTITY_VERSION,

          // client id will never be the same across the instances, ignore it
          CLIENT_ID
  );

  private final SynchronizationPlanner delegate;

  private final ObjectMapper objectMapper;

  public ModificationAwarePlanner(SynchronizationPlanner delegate) {
    this.objectMapper = new ObjectMapper();
    this.delegate = delegate;
  }

  /**
   * Removes keys from the provided map.
   *
   * @param map the map to remove the keys from
   * @param keysToRemove a list of keys, nested keys should be separated by '.' eg. "key1.key2"
   * @return the map with the keys removed
   */
  private Map<String, Object> removeKeys(Map<String, Object> map, List<String> keysToRemove) {
    Map<String, Object> cleaned =
        objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});

    for (String key : keysToRemove) {
      // splits key into first part and rest, eg. key1.key2.key3 becomes [key1, key2.key3]
      String[] separateFirst = key.split("\\.", 2);
      String primary = separateFirst[0];

      if (separateFirst.length > 1) {
        // if there are more nested keys, we want to recursively search the sub map if it exists
        Object valueForPrimary = cleaned.get(primary); // get object for primary key if it exists

        if (valueForPrimary == null) {
          continue;
        }

        try {
          Map<String, Object> subMap =
              objectMapper.convertValue(valueForPrimary, new TypeReference<>() {});
          Map<String, Object> cleanedSubMap =
              removeKeys(subMap, List.of(separateFirst[1])); // remove nested keys from submap
          cleaned.put(primary, cleanedSubMap); // replace sub-map with key removed
        } catch (IllegalArgumentException e) {
          // do nothing because that means the key does not exist, no need to remove it
        }
      } else {
        cleaned.remove(primary); // just remove the key if we have no more nesting
      }
    }

    return cleaned;
  }

  /**
   * Compares two objects to see if they are the same.
   *
   * @param o1
   * @param o2
   * @param keysToIgnore list of keys to ignore in the comparison
   * @return true if they are the same, false otherwise
   */
  private boolean areSame(Object o1, Object o2, List<String> keysToIgnore) {
    Map<String, Object> o1AsMap = objectMapper.convertValue(o1, new TypeReference<>() {});
    Map<String, Object> o2AsMap = objectMapper.convertValue(o2, new TypeReference<>() {});
    o1AsMap = removeKeys(o1AsMap, keysToIgnore);
    o2AsMap = removeKeys(o2AsMap, keysToIgnore);
    return o1AsMap.equals(o2AsMap);
  }

  private boolean areSame(Object o1, Object o2) {
    return areSame(o1, o2, DEFAULT_KEYS_TO_IGNORE);
  }

  /**
   * Container to represent the result of filtering out entities that are on both the source and the target
   * and have not been modified.
   * @param filteredEntitiesSource entities on the source that are do not exist on the
   *                               target or are different from the target for the same identifier
   * @param filteredEntitiesTarget entities on the target that do not exist on the source or are different from the
   *                               source for the same identifier
   * @param notModifiedEntities entities from the source that have not been modified since migration to the target
   * @param <T> the entity type
   */
  private record FilteredNotModifiedEntityResult<T>(
          List<T> filteredEntitiesSource, List<T> filteredEntitiesTarget, List<T> notModifiedEntities) {}

  /**
   * Filter out entities that are the same across the source and target Polaris instance.
   * @param entitiesOnSource the entities from the source
   * @param entitiesOnTarget the entities from the target
   * @param entityIdentifierSupplier supplies an identifier to identify the same entity on the source and target
   * @param entitiesAreSame compares entity with same identifier on source and target, returns true if they are the same,
   *                        false otherwise
   * @return the entities on the source and target with unmodified entities filtered out
   * @param <T> the entity type
   */
  private <T> FilteredNotModifiedEntityResult<T> filterOutEntitiesNotModified(
          Collection<T> entitiesOnSource,
          Collection<T> entitiesOnTarget,
          Function<T, Object> entityIdentifierSupplier,
          BiFunction<T, T, Boolean> entitiesAreSame
  ) {
    Map<Object, T> sourceEntitiesById = new HashMap<>();
    Map<Object, T> targetEntitiesById = new HashMap<>();

    List<T> notModifiedEntities = new ArrayList<>();

    entitiesOnSource.forEach(entity -> sourceEntitiesById.put(entityIdentifierSupplier.apply(entity), entity));
    entitiesOnTarget.forEach(entity -> targetEntitiesById.put(entityIdentifierSupplier.apply(entity), entity));

    for (T sourceEntity : entitiesOnSource) {
      Object sourceEntityId = entityIdentifierSupplier.apply(sourceEntity);
      if (targetEntitiesById.containsKey(sourceEntityId)) {
        T targetEntity = targetEntitiesById.get(sourceEntityId);
        Object targetEntityId = entityIdentifierSupplier.apply(targetEntity);

        if (entitiesAreSame.apply(sourceEntity, targetEntity)) {
          notModifiedEntities.add(sourceEntity);
          sourceEntitiesById.remove(sourceEntityId);
          targetEntitiesById.remove(targetEntityId);
        }
      }
    }

    return new FilteredNotModifiedEntityResult<>(
            sourceEntitiesById.values().stream().toList(),
            targetEntitiesById.values().stream().toList(),
            notModifiedEntities
    );
  }

  @Override
  public SynchronizationPlan<Principal> planPrincipalSync(List<Principal> principalsOnSource, List<Principal> principalsOnTarget) {
    FilteredNotModifiedEntityResult<Principal> result = filterOutEntitiesNotModified(
            principalsOnSource,
            principalsOnTarget,
            Principal::getName,
            (p1, p2) -> areSame(p1, p2, PRINCIPAL_KEYS_TO_IGNORE)
    );

    SynchronizationPlan<Principal> delegatedPlan = delegate.planPrincipalSync(
            result.filteredEntitiesSource(),
            result.filteredEntitiesTarget()
    );

    for (Principal principal : result.notModifiedEntities()) {
      delegatedPlan.skipEntityNotModified(principal);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<PrincipalRole> planAssignPrincipalsToPrincipalRolesSync(
          String principalName,
          List<PrincipalRole> assignedPrincipalRolesOnSource,
          List<PrincipalRole> assignedPrincipalRolesOnTarget
  ) {
    FilteredNotModifiedEntityResult<PrincipalRole> result = filterOutEntitiesNotModified(
            assignedPrincipalRolesOnSource,
            assignedPrincipalRolesOnTarget,
            PrincipalRole::getName,
            this::areSame
    );

    SynchronizationPlan<PrincipalRole> delegatedPlan =
            delegate.planAssignPrincipalsToPrincipalRolesSync(
                    principalName,
                    result.filteredEntitiesSource(),
                    result.filteredEntitiesTarget());

    for (PrincipalRole principalRole : result.notModifiedEntities()) {
      delegatedPlan.skipEntityNotModified(principalRole);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<PrincipalRole> planPrincipalRoleSync(
      List<PrincipalRole> principalRolesOnSource, List<PrincipalRole> principalRolesOnTarget) {
    FilteredNotModifiedEntityResult<PrincipalRole> result = filterOutEntitiesNotModified(
      principalRolesOnSource,
      principalRolesOnTarget,
      PrincipalRole::getName,
      this::areSame
    );

    SynchronizationPlan<PrincipalRole> delegatedPlan =
        delegate.planPrincipalRoleSync(
            result.filteredEntitiesSource(),
            result.filteredEntitiesTarget());

    for (PrincipalRole principalRole : result.notModifiedEntities()) {
      delegatedPlan.skipEntityNotModified(principalRole);
    }

    return delegatedPlan;
  }

  private boolean areSame(Catalog source, Catalog target) {
    return areSame(source, target, CATALOG_KEYS_TO_IGNORE)
        // because of the way the jackson serialization works, any class that extends HashMap is
        // serialized
        // with just the fields in the map. Unfortunately, CatalogProperties extends HashMap so we
        // must
        // manually compare the fields in the catalog properties and cannot automatically
        // deserialize them
        // as a map
        && Objects.equals(source.getProperties(), target.getProperties());
  }

  @Override
  public SynchronizationPlan<Catalog> planCatalogSync(
      List<Catalog> catalogsOnSource, List<Catalog> catalogsOnTarget) {
    FilteredNotModifiedEntityResult<Catalog> result = filterOutEntitiesNotModified(
            catalogsOnSource,
            catalogsOnTarget,
            Catalog::getName,
            this::areSame
    );

    SynchronizationPlan<Catalog> delegatedPlan =
        delegate.planCatalogSync(
            result.filteredEntitiesSource(),
            result.filteredEntitiesTarget());

    for (Catalog catalog : result.notModifiedEntities()) {
      delegatedPlan.skipEntityNotModified(catalog);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<CatalogRole> planCatalogRoleSync(
      String catalogName,
      List<CatalogRole> catalogRolesOnSource,
      List<CatalogRole> catalogRolesOnTarget) {
    FilteredNotModifiedEntityResult<CatalogRole> result = filterOutEntitiesNotModified(
            catalogRolesOnSource,
            catalogRolesOnTarget,
            CatalogRole::getName,
            this::areSame
    );

    SynchronizationPlan<CatalogRole> delegatedPlan =
        delegate.planCatalogRoleSync(
            catalogName,
            result.filteredEntitiesSource(),
            result.filteredEntitiesTarget());

    for (CatalogRole catalogRole : result.notModifiedEntities()) {
      delegatedPlan.skipEntityNotModified(catalogRole);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<GrantResource> planGrantSync(
      String catalogName,
      String catalogRoleName,
      List<GrantResource> grantsOnSource,
      List<GrantResource> grantsOnTarget) {
    FilteredNotModifiedEntityResult<GrantResource> result = filterOutEntitiesNotModified(
            grantsOnSource,
            grantsOnTarget,
            grant -> grant,
            GrantResource::equals
    );

    SynchronizationPlan<GrantResource> delegatedPlan =
        delegate.planGrantSync(
            catalogName,
            catalogRoleName,
            result.filteredEntitiesSource(),
            result.filteredEntitiesTarget());

    for (GrantResource grant : result.notModifiedEntities()) {
      delegatedPlan.skipEntityNotModified(grant);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<PrincipalRole> planAssignPrincipalRolesToCatalogRolesSync(
      String catalogName,
      String catalogRoleName,
      List<PrincipalRole> assignedPrincipalRolesOnSource,
      List<PrincipalRole> assignedPrincipalRolesOnTarget) {
    FilteredNotModifiedEntityResult<PrincipalRole> result = filterOutEntitiesNotModified(
            assignedPrincipalRolesOnSource,
            assignedPrincipalRolesOnTarget,
            PrincipalRole::getName,
            this::areSame
    );

    SynchronizationPlan<PrincipalRole> delegatedPlan = delegate.planAssignPrincipalRolesToCatalogRolesSync(
        catalogName,
        catalogRoleName,
        assignedPrincipalRolesOnSource,
        assignedPrincipalRolesOnTarget);

    for (PrincipalRole principalRole : result.notModifiedEntities()) {
      delegatedPlan.skipEntityNotModified(principalRole);
    }
    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<Namespace> planNamespaceSync(
      String catalogName,
      Namespace namespace,
      List<Namespace> namespacesOnSource,
      List<Namespace> namespacesOnTarget) {
    return delegate.planNamespaceSync(
        catalogName, namespace, namespacesOnSource, namespacesOnTarget);
  }

  @Override
  public SynchronizationPlan<TableIdentifier> planTableSync(
      String catalogName,
      Namespace namespace,
      Set<TableIdentifier> tablesOnSource,
      Set<TableIdentifier> tablesOnTarget) {
    return delegate.planTableSync(catalogName, namespace, tablesOnSource, tablesOnTarget);
  }
}
