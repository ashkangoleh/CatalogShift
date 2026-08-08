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

package org.apache.polaris.tools.sync.polaris.service;

import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around {@link org.apache.iceberg.catalog.Catalog} that exposes functionality
 * that uses multiple Iceberg operations. For example, cascading drops of namespaces.
 */
public interface IcebergCatalogService extends AutoCloseable {

    // NAMESPACES
    List<Namespace> listNamespaces(Namespace parentNamespace);
    Map<String, String> loadNamespaceMetadata(Namespace namespace);
    void createNamespace(Namespace namespace, Map<String, String> namespaceMetadata);
    void setNamespaceProperties(Namespace namespace, Map<String, String> namespaceProperties);

    /**
     * Drop a namespace by first dropping all nested namespaces and tables underneath the namespace
     * hierarchy. The root namespace, {@link Namespace#empty()}, will not be dropped.
     * @param namespace the namespace to drop
     */
    void dropNamespaceCascade(Namespace namespace);

    // TABLES
    List<TableIdentifier> listTables(Namespace namespace);
    Table loadTable(TableIdentifier tableIdentifier);
    void registerTable(TableIdentifier tableIdentifier, String metadataFileLocation);
    void dropTableWithoutPurge(TableIdentifier tableIdentifier);

}
