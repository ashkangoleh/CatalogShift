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

package org.apache.polaris.tools.sync.polaris.strategy;

import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.polaris.core.admin.model.Catalog;
import org.apache.polaris.core.admin.model.CatalogRole;
import org.apache.polaris.core.admin.model.GrantResource;
import org.apache.polaris.core.admin.model.Principal;
import org.apache.polaris.core.admin.model.PrincipalRole;
import org.apache.polaris.tools.sync.polaris.planning.BaseStrategyPlanner;
import org.apache.polaris.tools.sync.polaris.planning.SynchronizationPlanner;
import org.apache.polaris.tools.sync.polaris.planning.plan.SynchronizationPlan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class AbstractBaseStrategyPlannerTest {

    private final BaseStrategyPlanner.Strategy strategy;

    protected AbstractBaseStrategyPlannerTest(BaseStrategyPlanner.Strategy strategy) {
        this.strategy = strategy;
    }

    protected final Catalog CATALOG_1 = new Catalog().name("catalog-1");

    protected final Catalog CATALOG_2 = new Catalog().name("catalog-2");

    protected final Catalog CATALOG_3 = new Catalog().name("catalog-3");

    protected void testStrategy(Function<SynchronizationPlanner, SynchronizationPlan<?>> planSupplier,
                                Object entityToCreate,
                                Object entityToOverwrite,
                                Object entityToRemove) {
        testStrategy(planSupplier, true, entityToCreate, entityToOverwrite, entityToRemove);
    }

    /**
     * Test a generated plan for correctness in the case that there is 1 entity only on the source,
     * 1 entity on both source and target, and 1 entity only on target.
     * @param planSupplier generates the plan
     * @param requiresOverwrite if the entity requires a drop and recreate (most grant records do not)
     * @param entityOnSource the entity that is only on the source instance
     * @param entityOnBoth the entity that is on both instances
     * @param entityOnTarget the entity that is only on the target instance
     */
    protected void testStrategy(
            Function<SynchronizationPlanner, SynchronizationPlan<?>> planSupplier,
            boolean requiresOverwrite,
            Object entityOnSource,
            Object entityOnBoth,
            Object entityOnTarget
    ) {
        BaseStrategyPlanner planner = new BaseStrategyPlanner(strategy);

        SynchronizationPlan<?> plan = planSupplier.apply(planner);

        Assertions.assertTrue(plan.entitiesToCreate().contains(entityOnSource));
        Assertions.assertFalse(plan.entitiesToOverwrite().contains(entityOnSource));
        Assertions.assertFalse(plan.entitiesToRemove().contains(entityOnSource));

        if (!requiresOverwrite && strategy != BaseStrategyPlanner.Strategy.CREATE_ONLY) {
            // if the entity is not one that needs to be overwritten, then any strategy
            // besides CREATE_ONLY should instead schedule a "create" operation
            Assertions.assertTrue(plan.entitiesToCreate().contains(entityOnBoth));
        } else {
            Assertions.assertFalse(plan.entitiesToCreate().contains(entityOnBoth));
        }

        if (strategy == BaseStrategyPlanner.Strategy.CREATE_ONLY) {
            // make sure entities to overwrite are skipped in CREATE_ONLY mode
            Assertions.assertTrue(plan.entitiesToSkip().contains(entityOnBoth));
        } else if (requiresOverwrite) {
            Assertions.assertTrue(plan.entitiesToOverwrite().contains(entityOnBoth));
        }
        Assertions.assertFalse(plan.entitiesToRemove().contains(entityOnBoth));

        Assertions.assertFalse(plan.entitiesToCreate().contains(entityOnTarget));
        Assertions.assertFalse(plan.entitiesToOverwrite().contains(entityOnTarget));
        if (strategy == BaseStrategyPlanner.Strategy.REPLICATE) {
            Assertions.assertTrue(plan.entitiesToRemove().contains(entityOnTarget));
        } else {
            // only REPLICATE should remove entities from the target
            Assertions.assertTrue(plan.entitiesToSkipAndSkipChildren().contains(entityOnTarget));
        }
    }

    @Test
    public void testCatalogStrategy() {
        testStrategy(planner -> planner.planCatalogSync(
                        List.of(CATALOG_1, CATALOG_2), List.of(CATALOG_2, CATALOG_3)),
                CATALOG_1, CATALOG_2, CATALOG_3);
    }

    protected final Principal PRINCIPAL_1 = new Principal().name("principal-1");

    protected final Principal PRINCIPAL_2 = new Principal().name("principal-2");

    protected final Principal PRINCIPAL_3 = new Principal().name("principal-3");

    @Test
    public void testPrincipalStrategy() {
        testStrategy(planner -> planner.planPrincipalSync(
                List.of(PRINCIPAL_1, PRINCIPAL_2), List.of(PRINCIPAL_2, PRINCIPAL_3)),
                PRINCIPAL_1, PRINCIPAL_2, PRINCIPAL_3);
    }

    protected final PrincipalRole ASSIGNED_TO_PRINCIPAL_1 = new PrincipalRole().name("principal-role-1");

    protected final PrincipalRole ASSIGNED_TO_PRINCIPAL_2 = new PrincipalRole().name("principal-role-2");

    protected final PrincipalRole ASSIGNED_TO_PRINCIPAL_3 = new PrincipalRole().name("principal-role-3");

    @Test
    public void testAssignmentOfPrincipalRoleToPrincipalStrategy() {
        testStrategy(planner ->
                planner.planAssignPrincipalsToPrincipalRolesSync(
                        "principal",
                        List.of(ASSIGNED_TO_PRINCIPAL_1, ASSIGNED_TO_PRINCIPAL_2),
                        List.of(ASSIGNED_TO_PRINCIPAL_2, ASSIGNED_TO_PRINCIPAL_3)),
                false, /* requiresOverwrite */
                ASSIGNED_TO_PRINCIPAL_1, ASSIGNED_TO_PRINCIPAL_2, ASSIGNED_TO_PRINCIPAL_3);
    }

    protected final PrincipalRole PRINCIPAL_ROLE_1 = new PrincipalRole().name("principal-role-1");

    protected final PrincipalRole PRINCIPAL_ROLE_2 = new PrincipalRole().name("principal-role-2");

    protected final PrincipalRole PRINCIPAL_ROLE_3 = new PrincipalRole().name("principal-role-3");

    @Test
    public void testPrincipalRoleStrategy() {
        testStrategy(planner -> planner.planPrincipalRoleSync(
                        List.of(PRINCIPAL_ROLE_1, PRINCIPAL_ROLE_2),
                        List.of(PRINCIPAL_ROLE_2, PRINCIPAL_ROLE_3)),
                PRINCIPAL_ROLE_1, PRINCIPAL_ROLE_2, PRINCIPAL_ROLE_3);
    }

    protected final CatalogRole CATALOG_ROLE_1 = new CatalogRole().name("catalog-role-1");

    protected final CatalogRole CATALOG_ROLE_2 = new CatalogRole().name("catalog-role-2");

    protected final CatalogRole CATALOG_ROLE_3 = new CatalogRole().name("catalog-role-3");

    @Test
    public void testCatalogRoleStrategy() {
        testStrategy(planner ->
                planner.planCatalogRoleSync(
                        "catalog",
                        List.of(CATALOG_ROLE_1, CATALOG_ROLE_2),
                        List.of(CATALOG_ROLE_2, CATALOG_ROLE_3)),
                CATALOG_ROLE_1, CATALOG_ROLE_2, CATALOG_ROLE_3);

    }

    protected final GrantResource GRANT_1 = new GrantResource().type(GrantResource.TypeEnum.CATALOG);

    protected final GrantResource GRANT_2 = new GrantResource().type(GrantResource.TypeEnum.NAMESPACE);

    protected final GrantResource GRANT_3 = new GrantResource().type(GrantResource.TypeEnum.TABLE);

    @Test
    public void testCreatesNewGrantResourceRemovesDroppedGrantResource() {
        testStrategy(planner -> planner.planGrantSync(
                        "catalog", "catalogRole",
                        List.of(GRANT_1, GRANT_2), List.of(GRANT_2, GRANT_3)),
                false, /* requiresOverwrite */
                GRANT_1, GRANT_2, GRANT_3);
    }

    protected final PrincipalRole ASSIGNED_TO_CATALOG_ROLE_1 = new PrincipalRole().name("principal-role-1");

    protected final PrincipalRole ASSIGNED_TO_CATALOG_ROLE_2 = new PrincipalRole().name("principal-role-2");

    protected final PrincipalRole ASSIGNED_TO_CATALOG_ROLE_3 = new PrincipalRole().name("principal-role-3");

    @Test
    public void testAssignPrincipalRoleToCatalogRoleStrategy() {
        testStrategy(planner ->
                planner.planAssignPrincipalRolesToCatalogRolesSync(
                        "catalog",
                        "catalogRole",
                        List.of(ASSIGNED_TO_CATALOG_ROLE_1, ASSIGNED_TO_CATALOG_ROLE_2),
                        List.of(ASSIGNED_TO_CATALOG_ROLE_2, ASSIGNED_TO_CATALOG_ROLE_3)),
                        false, /* requiresOverwrite */
                        ASSIGNED_TO_CATALOG_ROLE_1, ASSIGNED_TO_CATALOG_ROLE_2, ASSIGNED_TO_CATALOG_ROLE_3);
    }

    protected final Namespace NS_1 = Namespace.of("ns1");

    protected final Namespace NS_2 = Namespace.of("ns2");

    protected final Namespace NS_3 = Namespace.of("ns3");

    @Test
    public void testNamespaceStrategy() {
        testStrategy(planner -> planner.planNamespaceSync(
                "catalog", Namespace.empty(), List.of(NS_1, NS_2), List.of(NS_2, NS_3)),
                NS_1, NS_2, NS_3);
    }

    protected final TableIdentifier TABLE_1 = TableIdentifier.of("ns", "table1");

    protected final TableIdentifier TABLE_2 = TableIdentifier.of("ns", "table2");

    protected final TableIdentifier TABLE_3 = TableIdentifier.of("ns", "table3");

    @Test
    public void testTableStrategy() {
        testStrategy(planner ->
                planner.planTableSync(
                        "catalog", Namespace.empty(), Set.of(TABLE_1, TABLE_2), Set.of(TABLE_2, TABLE_3)),
                TABLE_1, TABLE_2, TABLE_3);
    }

}
