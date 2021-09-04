/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.server.springboot.samples;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.definition.ProcessDefinition;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListImagesCmd;


@Testcontainers(disabledWithoutDocker=true)
public class ImmutableSpringbootSystemTest{

    static final String ARTIFACT_ID = "test-sample";
    static final String GROUP_ID = "org.kie.server.testing";
    static final String VERSION = "1.0.0";
    
    public static final String user = "kieserver";
    public static final String password = "kieserver1!";
    
    private static final Logger logger = LoggerFactory.getLogger(ImmutableSpringbootSystemTest.class);
    
    static final String ALIAS = "eval";
    static final String CONTAINER_ID = ARTIFACT_ID+"-"+VERSION;
    static final String PROCESS_ID = "evaluation";
    
    private KieServicesClient kieServicesClient;
    
    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>(System.getProperty("org.kie.samples.image.postgresql","postgres:latest"))
            .withDatabaseName("rhpamdatabase")
            .withUsername("rhpamuser")
            .withPassword("rhpampassword").withNetwork(Network.SHARED)
            .withFileSystemBind("./target/etc/postgresql", "/docker-entrypoint-initdb.d",
                    BindMode.READ_ONLY)
            .withNetworkAliases("db");
    
    @Container
    static SpringbootKieContainer springboot1 = new SpringbootKieContainer("test-sb-1", "KieServer-SpringBoot-Test-1");
        
    
    @BeforeAll
    static void startTestContainers() {
        assumeTrue(isDockerAvailable());
    }
    
    @BeforeEach
    public void beforeEach() throws InterruptedException {
        ReleaseId releaseId = new ReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        
        String serverUrl = "http://localhost:" + springboot1.getKiePort() + "/rest/server";
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(serverUrl, user, password);
        configuration.setTimeout(60000);
        configuration.setMarshallingFormat(MarshallingFormat.JSON);
        this.kieServicesClient = KieServicesFactory.newKieServicesClient(configuration);
    }

    //@AfterAll
    static void tearDown() throws Exception {
        springboot1.stop();
        DockerClient docker = DockerClientFactory.instance().client();
        ListImagesCmd listImagesCmd = docker.listImagesCmd();
        listImagesCmd.getFilters().put("reference", Arrays.asList("local/kie-server-springboot"));
        listImagesCmd.exec().stream()
         .filter(c -> c.getId() != null)
         .forEach(c -> docker.removeImageCmd(c.getId()).withForce(true).exec());
    }

    @BeforeEach
    void setup() {
        
        logger.info("KIE_SPRINGBOOT1 started at {}:{}", springboot1.getContainerIpAddress(), springboot1.getKiePort());
        
    }
    
    @AfterEach
    void cleanup() {
        /*if (deploymentService!=null) {
            deploymentService.undeploy(unit);
        }
        if (kieServicesClient != null) {
            kieServicesClient.disposeContainer(containerId);
        }*/
    }
    

    
    private static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }
    
    @Test
    public void testProcessStartAndAbort() {

        // query for all available process definitions
        QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        List<ProcessDefinition> processes = queryClient.findProcesses(0, 10);
        assertEquals(1, processes.size());

        ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
        // get details of process definition
        ProcessDefinition definition = processClient.getProcessDefinition(CONTAINER_ID, PROCESS_ID);
        assertNotNull(definition);
        assertEquals(PROCESS_ID, definition.getId());

        // start process instance
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", "john");
        params.put("reason", "test on spring boot");
        Long processInstanceId = processClient.startProcess(CONTAINER_ID, PROCESS_ID, params);
        assertNotNull(processInstanceId);

        // find active process instances
        List<ProcessInstance> instances = queryClient.findProcessInstances(0, 10);
        assertEquals(1, instances.size());

        // at the end abort process instance
        processClient.abortProcessInstance(CONTAINER_ID, processInstanceId);

        ProcessInstance processInstance = queryClient.findProcessInstanceById(processInstanceId);
        assertNotNull(processInstance);
        assertEquals(3, processInstance.getState().intValue());
    }
    
}


