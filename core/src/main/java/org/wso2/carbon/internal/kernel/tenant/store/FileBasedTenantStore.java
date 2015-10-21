/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.internal.kernel.tenant.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.internal.kernel.DefaultImplConstants;
import org.wso2.carbon.internal.kernel.tenant.DefaultTenant;
import org.wso2.carbon.internal.kernel.tenant.store.model.AdminUserConfig;
import org.wso2.carbon.internal.kernel.tenant.store.model.HierarchyConfig;
import org.wso2.carbon.internal.kernel.tenant.store.model.TenantConfig;
import org.wso2.carbon.internal.kernel.tenant.store.model.TenantStoreConfig;
import org.wso2.carbon.kernel.CarbonConstants;
import org.wso2.carbon.kernel.tenant.Tenant;
import org.wso2.carbon.kernel.tenant.store.TenantStore;
import org.wso2.carbon.kernel.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 * TODO: class level comment
 */
//TODO Implement transactional behaviour (Implement locking mechanism)
public class FileBasedTenantStore implements TenantStore<Tenant> {

    private static final Logger logger = LoggerFactory.getLogger(FileBasedTenantStore.class);

    private TenantStoreConfig tenantStoreConfig;
    private JAXBContext jaxbContext;
    private Map<String, TenantConfig> tenantConfigMap;

    private String tenantStoreXMLPath = Utils.getCarbonHome() + File.separator + CarbonConstants.DATA_REPO_DIR +
            File.separator + DefaultImplConstants.TENANT_STORE_XML;

    public void init() throws Exception {
        // Initializing the JAXBContext
        jaxbContext = JAXBContext.newInstance(TenantStoreConfig.class);

        // Loading the tenant's data
        // We have the option of loading this lazily.
        loadConfig();
    }

    @Override
    public Tenant loadTenant(String tenantDomain) throws Exception {
        if (tenantConfigMap.containsKey(tenantDomain)) {
            TenantConfig tenantConfig = tenantConfigMap.get(tenantDomain);
            return populateTenant(tenantConfig);
        }

        throw new Exception("Tenant with the domain " + tenantDomain + " does not exists");
    }

    @Override
    public void persistTenant(Tenant tenant) throws Exception {
        TenantConfig tenantConfig = populateTenantConfig(tenant);
        tenantStoreConfig.addTenantConfig(tenantConfig);
        saveConfig();
    }

    @Override
    public Tenant deleteTenant(String tenantDomain) throws Exception {

//        TenantConfig tenantConfig = tenantConfigMap.get(tenantDomain);
//        if(tenantConfig == null) {
//            throw new Exception("Tenant with domain " + tenantDomain + " does not exists");
//        }

//        tenantConfigMap.remove(tenantDomain);
//        tenantStoreConfig.getTenantConfigs();
        throw new UnsupportedOperationException();
    }

    private void saveConfig() throws Exception {
        try (Writer writer =
                     new OutputStreamWriter(new FileOutputStream(tenantStoreXMLPath), StandardCharsets.ISO_8859_1)) {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(tenantStoreConfig, writer);
        } catch (IOException | JAXBException e) {
            logger.error("Error occurred while saving " + tenantStoreXMLPath, e);
            throw e;
        }
    }

    private void loadConfig() throws Exception {
        try (Reader reader =
                     new InputStreamReader(new FileInputStream(tenantStoreXMLPath), StandardCharsets.ISO_8859_1)) {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            tenantStoreConfig = (TenantStoreConfig) unmarshaller.unmarshal(reader);
            populateTenantConfigMap();
        } catch (FileNotFoundException | JAXBException e) {
            logger.error("Could not load " + tenantStoreXMLPath, e);
            throw e;
        }
    }

    private Tenant populateTenant(TenantConfig tenantConfig) {
        Tenant tenant = new DefaultTenant();
        tenant.setId(tenantConfig.getId());
        tenant.setDomain(tenantConfig.getDomain());
        tenant.setName(tenantConfig.getName());
        tenant.setDescription(tenantConfig.getDescription());
        tenant.setCreatedDate(tenantConfig.getCreatedDate());

        tenant.setAdminUsername(tenantConfig.getAdminUserConfig().getName());
        tenant.setAdminUserEmailAddress(tenantConfig.getAdminUserConfig().getEmailAddress());

        //TODO Add hierarchy information
        return tenant;
    }

    private TenantConfig populateTenantConfig(Tenant tenant) {
        TenantConfig tenantConfig = new TenantConfig();
        tenantConfig.setId(tenant.getId());
        tenantConfig.setDomain(tenant.getDomain());
        tenantConfig.setName(tenant.getName());
        tenantConfig.setDescription(tenant.getDescription());
        tenantConfig.setCreatedDate(tenant.getCreatedDate());

        AdminUserConfig adminUserConfig = new AdminUserConfig();
        adminUserConfig.setName(tenant.getAdminUsername());
        adminUserConfig.setEmailAddress(tenant.getAdminUserEmailAddress());
        tenantConfig.setAdminUserConfig(adminUserConfig);

        tenantConfig.setHierarchyConfig(populateHierarchyConfig(tenant));
        //TODO add properties or attributes
        return tenantConfig;
    }

    private HierarchyConfig populateHierarchyConfig(Tenant tenant) {
        HierarchyConfig hierarchyConfig = new HierarchyConfig();

        if (tenant.getParent() != null) {
            hierarchyConfig.setParentID(tenant.getParent().getId());
            hierarchyConfig.setDepthOfHierarchy(tenant.getParent().getDepthOfHierarchy() - 1);
        } else {
            hierarchyConfig.setParentID("Server");
            hierarchyConfig.setDepthOfHierarchy(tenant.getDepthOfHierarchy());
        }
        return hierarchyConfig;
    }

    private void populateTenantConfigMap() {
        tenantConfigMap = new HashMap<>(tenantStoreConfig.getTenantConfigs().size());
        for (TenantConfig tenantConfig : tenantStoreConfig.getTenantConfigs()) {
            tenantConfigMap.put(tenantConfig.getDomain(), tenantConfig);
        }
    }
}