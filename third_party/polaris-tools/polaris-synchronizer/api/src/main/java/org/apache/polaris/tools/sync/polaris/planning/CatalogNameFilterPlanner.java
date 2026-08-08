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

import org.apache.polaris.core.admin.model.Catalog;
import org.apache.polaris.tools.sync.polaris.planning.plan.SynchronizationPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Planner that filters out catalogs from the source and target on the basis of a RegEx before
 * they are passed to an encapsulated planner.
 */
public class CatalogNameFilterPlanner extends DelegatedPlanner implements SynchronizationPlanner {

    private final String catalogNameFilterPattern;

    public CatalogNameFilterPlanner(String regex, SynchronizationPlanner delegate) {
        super(delegate);
        this.catalogNameFilterPattern = regex;
    }

    @Override
    public SynchronizationPlan<Catalog> planCatalogSync(List<Catalog> catalogsOnSource, List<Catalog> catalogsOnTarget) {
        List<Catalog> filteredSourceCatalogs = new ArrayList<>();

        // store the names of the catalogs we skip so that we don't also mark target catalogs with the same name
        // twice
        Map<String, Catalog> skippedSourceCatalogsByName = new HashMap<>();

        for (Catalog catalog : catalogsOnSource) {
            if (catalog.getName().matches(catalogNameFilterPattern)) {
                filteredSourceCatalogs.add(catalog);
            } else {
                skippedSourceCatalogsByName.put(catalog.getName(), catalog);
            }
        }

        List<Catalog> filteredTargetCatalogs = new ArrayList<>();

        List<Catalog> skippedTargetCatalogs = new ArrayList<>();

        for (Catalog catalog : catalogsOnTarget) {
            if (catalog.getName().matches(catalogNameFilterPattern)) {
                filteredTargetCatalogs.add(catalog);
            } else if (!skippedSourceCatalogsByName.containsKey(catalog.getName())) {
                // if we already skipped a catalog with the same name on the source, we don't want to mark it as
                // skipped again, but we do want to mark catalogs that aren't on the source but were instead filtered
                // out solely from the target
                skippedTargetCatalogs.add(catalog);
            }
        }

        SynchronizationPlan<Catalog> delegatedPlan =
                delegate.planCatalogSync(filteredSourceCatalogs, filteredTargetCatalogs);

        skippedSourceCatalogsByName.values().forEach(delegatedPlan::skipEntityAndSkipChildren);
        skippedTargetCatalogs.forEach(delegatedPlan::skipEntityAndSkipChildren);

        return delegatedPlan;
    }
}
