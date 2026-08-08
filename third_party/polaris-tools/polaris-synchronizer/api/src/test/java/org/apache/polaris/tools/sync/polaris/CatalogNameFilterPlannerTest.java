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

package org.apache.polaris.tools.sync.polaris;

import org.apache.polaris.core.admin.model.Catalog;
import org.apache.polaris.tools.sync.polaris.planning.CatalogNameFilterPlanner;
import org.apache.polaris.tools.sync.polaris.planning.NoOpSyncPlanner;
import org.apache.polaris.tools.sync.polaris.planning.SynchronizationPlanner;
import org.apache.polaris.tools.sync.polaris.planning.plan.SynchronizationPlan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CatalogNameFilterPlannerTest {

    private static final Catalog CATALOG_1 = new Catalog().name("catalog-1");

    private static final Catalog CATALOG_2 = new Catalog().name("catalog-2");

    @Test
    public void testFiltersOutCatalog() {
        SynchronizationPlanner planner = new CatalogNameFilterPlanner(
                "^catalog-1$", new NoOpSyncPlanner());

        SynchronizationPlan<Catalog> plan
                = planner.planCatalogSync(List.of(CATALOG_1, CATALOG_2), List.of());

        Assertions.assertTrue(plan.entitiesToSkipAndSkipChildren().contains(CATALOG_2));
        Assertions.assertFalse(plan.entitiesToSkipAndSkipChildren().contains(CATALOG_1));
    }

    @Test
    public void onlyMarksSourceCatalogForFilteringWhenCatalogIsOnSourceAndTarget() {
        SynchronizationPlanner planner = new CatalogNameFilterPlanner(
                "^something that doesn't match the catalog name$", new NoOpSyncPlanner());

        SynchronizationPlan<Catalog> plan
                = planner.planCatalogSync(List.of(CATALOG_1), List.of(CATALOG_1));

        Assertions.assertTrue(plan.entitiesToSkipAndSkipChildren().contains(CATALOG_1));

        // ensure only marks the source catalog, doesn't also mark target catalog as well
        Assertions.assertEquals(1, plan.entitiesToSkipAndSkipChildren().size());
    }

    @Test
    public void marksTargetCatalogWhenSourceCatalogDoesNotExist() {
        SynchronizationPlanner planner = new CatalogNameFilterPlanner(
                "^something that doesn't match either catalog name$", new NoOpSyncPlanner());

        SynchronizationPlan<Catalog> plan
                = planner.planCatalogSync(List.of(CATALOG_1), List.of(CATALOG_2));

        Assertions.assertTrue(plan.entitiesToSkipAndSkipChildren().contains(CATALOG_1));
        Assertions.assertTrue(plan.entitiesToSkipAndSkipChildren().contains(CATALOG_2));
    }
}
