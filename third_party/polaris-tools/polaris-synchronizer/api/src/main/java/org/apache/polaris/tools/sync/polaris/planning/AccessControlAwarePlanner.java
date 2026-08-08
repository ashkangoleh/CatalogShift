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

import java.util.ArrayList;
import java.util.List;
import org.apache.polaris.core.admin.model.CatalogRole;
import org.apache.polaris.core.admin.model.Principal;
import org.apache.polaris.core.admin.model.PrincipalRole;
import org.apache.polaris.tools.sync.polaris.access.AccessControlConstants;
import org.apache.polaris.tools.sync.polaris.planning.plan.SynchronizationPlan;

/**
 * Planner that filters out access control entities that should not be modified in the duration of
 * the sync. This includes the omnipotent roles and principals that we do not want to copy between
 * the two instances as well as modifications to service_admin or catalog_admin that may disrupt
 * manage_access permissions.
 */
public class AccessControlAwarePlanner extends DelegatedPlanner implements SynchronizationPlanner {

  public AccessControlAwarePlanner(SynchronizationPlanner delegate) {
    super(delegate);
  }

  @Override
  public SynchronizationPlan<Principal> planPrincipalSync(
          List<Principal> principalsOnSource, List<Principal> principalsOnTarget) {
    List<Principal> skippedPrincipals = new ArrayList<>();
    List<Principal> filteredPrincipalsSource = new ArrayList<>();
    List<Principal> filteredPrincipalsTarget = new ArrayList<>();

    for (Principal principal : principalsOnSource) {
      // if the principal is the omnipotent principal do not sync it to target
      if (principal.getProperties() != null
              && principal.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedPrincipals.add(principal);
        continue;
      }

      // do not modify root principal
      if (principal.getName().equals("root")) {
        skippedPrincipals.add(principal);
        continue;
      }

      filteredPrincipalsSource.add(principal);
    }

    for (Principal principal : principalsOnTarget) {
      // if the principal is the omnipotent principal, ensure it is not modified
      // on the target
      if (principal.getProperties() != null
              && principal.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedPrincipals.add(principal);
        continue;
      }

      // do not modify root principal
      if (principal.getName().equals("root")) {
        skippedPrincipals.add(principal);
        continue;
      }

      filteredPrincipalsTarget.add(principal);
    }

    SynchronizationPlan<Principal> delegatedPlan =
            delegate.planPrincipalSync(filteredPrincipalsSource, filteredPrincipalsTarget);

    for (Principal principal : skippedPrincipals) {
      delegatedPlan.skipEntityAndSkipChildren(principal);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<PrincipalRole> planAssignPrincipalsToPrincipalRolesSync(
          String principalName,
          List<PrincipalRole> assignedPrincipalRolesOnSource,
          List<PrincipalRole> assignedPrincipalRolesOnTarget
  ) {
    List<PrincipalRole> skippedRoles = new ArrayList<>();
    List<PrincipalRole> filteredRolesSource = new ArrayList<>();
    List<PrincipalRole> filteredRolesTarget = new ArrayList<>();

    for (PrincipalRole role : assignedPrincipalRolesOnSource) {
      // filter out assignment to omnipotent principal role
      if (role.getProperties() != null
              && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out assignment to service admin
      if (role.getName().equals("service_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesSource.add(role);
    }

    for (PrincipalRole role : assignedPrincipalRolesOnTarget) {
      // filer out assignment to omnipotent principal role
      if (role.getProperties() != null
              && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out assignment to service admin
      if (role.getName().equals("service_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesTarget.add(role);
    }

    SynchronizationPlan<PrincipalRole> delegatedPlan =
            this.delegate.planAssignPrincipalsToPrincipalRolesSync(
                    principalName, filteredRolesSource, filteredRolesTarget);

    for (PrincipalRole role : skippedRoles) {
      delegatedPlan.skipEntity(role);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<PrincipalRole> planPrincipalRoleSync(
      List<PrincipalRole> principalRolesOnSource, List<PrincipalRole> principalRolesOnTarget) {
    List<PrincipalRole> skippedRoles = new ArrayList<>();
    List<PrincipalRole> filteredRolesSource = new ArrayList<>();
    List<PrincipalRole> filteredRolesTarget = new ArrayList<>();

    for (PrincipalRole role : principalRolesOnSource) {
      // filter out omnipotent principal role
      if (role.getProperties() != null
          && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out service_admin
      if (role.getName().equals("service_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesSource.add(role);
    }

    for (PrincipalRole role : principalRolesOnTarget) {
      // filter out omnipotent principal role
      if (role.getProperties() != null
          && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out service admin
      if (role.getName().equals("service_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesTarget.add(role);
    }

    SynchronizationPlan<PrincipalRole> delegatedPlan =
        this.delegate.planPrincipalRoleSync(filteredRolesSource, filteredRolesTarget);

    for (PrincipalRole role : skippedRoles) {
      delegatedPlan.skipEntity(role);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<CatalogRole> planCatalogRoleSync(
      String catalogName,
      List<CatalogRole> catalogRolesOnSource,
      List<CatalogRole> catalogRolesOnTarget) {
    List<CatalogRole> skippedRoles = new ArrayList<>();
    List<CatalogRole> filteredRolesSource = new ArrayList<>();
    List<CatalogRole> filteredRolesTarget = new ArrayList<>();

    for (CatalogRole role : catalogRolesOnSource) {
      // filter out omnipotent catalog role
      if (role.getProperties() != null
          && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out catalog admin
      if (role.getName().equals("catalog_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesSource.add(role);
    }

    for (CatalogRole role : catalogRolesOnTarget) {
      // filter out omnipotent catalog role
      if (role.getProperties() != null
          && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out catalog admin
      if (role.getName().equals("catalog_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesTarget.add(role);
    }

    SynchronizationPlan<CatalogRole> delegatedPlan =
        this.delegate.planCatalogRoleSync(catalogName, filteredRolesSource, filteredRolesTarget);

    for (CatalogRole role : skippedRoles) {
      delegatedPlan.skipEntityAndSkipChildren(role);
    }

    return delegatedPlan;
  }

  @Override
  public SynchronizationPlan<PrincipalRole> planAssignPrincipalRolesToCatalogRolesSync(
      String catalogName,
      String catalogRoleName,
      List<PrincipalRole> assignedPrincipalRolesOnSource,
      List<PrincipalRole> assignedPrincipalRolesOnTarget) {
    List<PrincipalRole> skippedRoles = new ArrayList<>();
    List<PrincipalRole> filteredRolesSource = new ArrayList<>();
    List<PrincipalRole> filteredRolesTarget = new ArrayList<>();

    for (PrincipalRole role : assignedPrincipalRolesOnSource) {
      // filter out assignment to omnipotent catalog role
      if (role.getProperties() != null
          && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out assignment to service admin
      if (role.getName().equals("service_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesSource.add(role);
    }

    for (PrincipalRole role : assignedPrincipalRolesOnTarget) {
      // filer out assignment to omnipotent principal role
      if (role.getProperties() != null
          && role.getProperties().containsKey(AccessControlConstants.OMNIPOTENCE_PROPERTY)) {
        skippedRoles.add(role);
        continue;
      }

      // filter out assignment to service admin
      if (role.getName().equals("service_admin")) {
        skippedRoles.add(role);
        continue;
      }

      filteredRolesTarget.add(role);
    }

    SynchronizationPlan<PrincipalRole> delegatedPlan =
        this.delegate.planAssignPrincipalRolesToCatalogRolesSync(
            catalogName, catalogRoleName, filteredRolesSource, filteredRolesTarget);

    for (PrincipalRole role : skippedRoles) {
      delegatedPlan.skipEntity(role);
    }

    return delegatedPlan;
  }
}
