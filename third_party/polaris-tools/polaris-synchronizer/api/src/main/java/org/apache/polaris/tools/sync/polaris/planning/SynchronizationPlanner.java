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
import java.util.Set;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.polaris.core.admin.model.Catalog;
import org.apache.polaris.core.admin.model.CatalogRole;
import org.apache.polaris.core.admin.model.GrantResource;
import org.apache.polaris.core.admin.model.Principal;
import org.apache.polaris.core.admin.model.PrincipalRole;
import org.apache.polaris.tools.sync.polaris.planning.plan.SynchronizationPlan;

/**
 * Generic interface to generate synchronization plans for different types of entities based on what
 * principal roles exist on the source and target.
 */
public interface SynchronizationPlanner {

  class SynchronizationPlannerBuilder {

    @FunctionalInterface
    public interface PlannerWrapper {

      /**
       * Wrap a provided {@link SynchronizationPlanner} by another {@link SynchronizationPlanner}.
       * @param planner the planner to wrap
       * @return a wrapped planner
       */
      SynchronizationPlanner wrap(SynchronizationPlanner planner);
    }

    private final SynchronizationPlanner innermost;

    private final List<PlannerWrapper> plannerWrappers = new ArrayList<>();

    private SynchronizationPlannerBuilder(BaseStrategyPlanner innermost) {
      this.innermost = innermost;
    }

    /**
     * Wrap the current chain of planners.
     * @param outer the planner to wrap by
     */
    public SynchronizationPlannerBuilder wrapBy(PlannerWrapper outer) {
      plannerWrappers.add(outer);
      return this;
    }

    /**
     * Wrap the current chain of planners if the condition is true.
     * @param condition if true, will wrap the current chain of planners by the provided outer planner
     * @param outer the planner to wrap by
     */
    public SynchronizationPlannerBuilder conditionallyWrapBy(boolean condition, PlannerWrapper outer) {
      if (condition) {
        plannerWrappers.add(outer);
      }
      return this;
    }

    /**
     * Build the chained set of planners.
     */
    public SynchronizationPlanner build() {
      SynchronizationPlanner current = innermost;
      for (PlannerWrapper plannerWrapper : plannerWrappers) {
        current = plannerWrapper.wrap(current);
      }
      return current;
    }
  }

  static SynchronizationPlannerBuilder builder(BaseStrategyPlanner innermost) {
    return new SynchronizationPlannerBuilder(innermost);
  }

  SynchronizationPlan<Principal> planPrincipalSync(
          List<Principal> principalsOnSource, List<Principal> principalsOnTarget);

  SynchronizationPlan<PrincipalRole> planAssignPrincipalsToPrincipalRolesSync(
          String principalName,
          List<PrincipalRole> assignedPrincipalRolesOnSource,
          List<PrincipalRole> assignedPrincipalRolesOnTarget);

  SynchronizationPlan<PrincipalRole> planPrincipalRoleSync(
      List<PrincipalRole> principalRolesOnSource, List<PrincipalRole> principalRolesOnTarget);

  SynchronizationPlan<Catalog> planCatalogSync(
      List<Catalog> catalogsOnSource, List<Catalog> catalogsOnTarget);

  SynchronizationPlan<CatalogRole> planCatalogRoleSync(
      String catalogName,
      List<CatalogRole> catalogRolesOnSource,
      List<CatalogRole> catalogRolesOnTarget);

  SynchronizationPlan<GrantResource> planGrantSync(
      String catalogName,
      String catalogRoleName,
      List<GrantResource> grantsOnSource,
      List<GrantResource> grantsOnTarget);

  SynchronizationPlan<PrincipalRole> planAssignPrincipalRolesToCatalogRolesSync(
      String catalogName,
      String catalogRoleName,
      List<PrincipalRole> assignedPrincipalRolesOnSource,
      List<PrincipalRole> assignedPrincipalRolesOnTarget);

  SynchronizationPlan<Namespace> planNamespaceSync(
      String catalogName,
      Namespace namespace,
      List<Namespace> namespacesOnSource,
      List<Namespace> namespacesOnTarget);

  SynchronizationPlan<TableIdentifier> planTableSync(
      String catalogName,
      Namespace namespace,
      Set<TableIdentifier> tablesOnSource,
      Set<TableIdentifier> tablesOnTarget);
}