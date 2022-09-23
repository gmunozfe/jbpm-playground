/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
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

package org.kie.samples.integration;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDOUT;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.FIVE_SECONDS;
import static org.awaitility.Duration.ONE_MINUTE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.kie.samples.integration.testcontainers.KieServerContainer;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.github.dockerjava.api.DockerClient;

@Testcontainers(disabledWithoutDocker=true)
public class FailoverJobsSystemTest {
    
    private static final String JMX_PASSWORD = "jmxPassword1!";
    private static final String JMX_USER = "jmxUser";
    private static final String CLUSTER_CACHE_STATS_OBJECT = "org.wildfly.clustering.infinispan:type=Cache,name=\"jobs(repl_sync)\",manager=\"jbpm\",component=ClusterCacheStats";
    private static final String FAILOVER_JOBS_PROCESS = "failover_jobs_process";
    private static final String ARTIFACT_ID = "failover-jobs-sample";
    private static final String GROUP_ID = "org.kie.server.testing";
    private static final String VERSION = "1.0.0";
    private static final String ALIAS = "-alias";
    
    private static final String DEFAULT_USER = "kieserver";
    private static final String DEFAULT_PASSWORD = "kieserver1!";

    public static String containerId = GROUP_ID+":"+ARTIFACT_ID+":"+VERSION;

    private static Logger logger = LoggerFactory.getLogger(FailoverJobsSystemTest.class);
    
    private static Map<String, String> args = new HashMap<>();

    static {
        args.put("IMAGE_NAME_node1", System.getProperty("org.kie.samples.image.node1"));
        args.put("IMAGE_NAME_node2", System.getProperty("org.kie.samples.image.node2"));
        args.put("START_SCRIPT_node1", System.getProperty("org.kie.samples.script.node1"));
        args.put("START_SCRIPT_node2", System.getProperty("org.kie.samples.script.node2"));
        args.put("SERVER_node1", System.getProperty("org.kie.samples.server.node1"));
        args.put("SERVER_node2", System.getProperty("org.kie.samples.server.node2"));
    }

    @ClassRule
    public static Network network = Network.newNetwork();
    
    @ClassRule
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>(System.getProperty("org.kie.samples.image.postgresql","postgres:latest"))
                                        .withDatabaseName("rhpamdatabase")
                                        .withUsername("rhpamuser")
                                        .withPassword("rhpampassword")
                                        .withCommand("-c max_prepared_transactions=100")
                                        .withNetwork(network)
                                        .withNetworkAliases("postgresql11");

    @ClassRule
    public static KieServerContainer kieServer1 = new KieServerContainer("node1", network, args);
    
    @ClassRule
    public static KieServerContainer kieServer2 = new KieServerContainer("node2", network, args);
    
    private static KieServicesClient ksClient1;
    private static KieServicesClient ksClient2;
    
    private static ProcessServicesClient processClient2;
    
    private static WaitingConsumer consumerCluster;
    
    @BeforeClass
    public static void setup() throws IOException, MalformedObjectNameException {
        logger.info("KIE SERVER 1 started "+kieServer1.getContainerIpAddress()+" ; port "+kieServer1.getKiePort()+" ; jmx:"+kieServer1.getJMXPort());
        logger.info("KIE SERVER 2 started "+kieServer2.getContainerIpAddress()+" ; port "+kieServer2.getKiePort()+" ; jmx:"+kieServer2.getJMXPort());
        logger.info("postgresql started at "+postgreSQLContainer.getJdbcUrl());
        
        ksClient1 = authenticate(kieServer1.getKiePort(), DEFAULT_USER, DEFAULT_PASSWORD);
        ksClient2 = authenticate(kieServer2.getKiePort(), DEFAULT_USER, DEFAULT_PASSWORD);
        
        processClient2 = ksClient2.getServicesClient(ProcessServicesClient.class);
        
        createContainer(ksClient1);
        createContainer(ksClient2);
        
        consumerCluster = new WaitingConsumer();
        kieServer1.followOutput(consumerCluster, STDOUT);
        kieServer2.followOutput(consumerCluster, STDOUT);
        
    }

    @AfterClass
    public static void tearDown() throws Exception {
        ksClient1.disposeContainer(containerId);
        DockerClient docker = DockerClientFactory.instance().client();
        docker.listImagesCmd().withLabelFilter("autodelete=true").exec().stream()
         .filter(c -> c.getId() != null)
         .forEach(c -> docker.removeImageCmd(c.getId()).withForce(true).exec());
    }

    @Test
    @DisplayName("test completed async jobs are removed from cache and not retrieved by second node in the cluster when first goes down")
    public void testAsyncJobInClusterWhenNodeGoesDown() throws Exception {
        startProcessInNode2(FAILOVER_JOBS_PROCESS, 2000);
        startProcessInNode2(FAILOVER_JOBS_PROCESS, 20000);
        
        assertEquals("there should be 3 put operations in the cache before job execution and removal", 
                3, (long) getJMXAttribute(kieServer2.getJMXPort(), CLUSTER_CACHE_STATS_OBJECT, "stores"));
        
        logger.info("Waiting for job 1 completion ");
        await().atMost(ONE_MINUTE)
          .until(() -> kieServer2.getLogs(STDOUT).contains("Request job 1 has been completed"));
        
        assertTrue(kieServer2.getLogs(STDOUT).contains("Removing executed job RequestInfo{id=1"));
        
        //There should be 4 put operations in the cache after job execution and removal"
        await().atMost(FIVE_SECONDS)
          .until(() -> (long) getJMXAttribute(kieServer2.getJMXPort(), CLUSTER_CACHE_STATS_OBJECT, "stores") == 4);
        
        logger.info("****************************  KieServer 2 stopping .... ***********************************************");
        kieServer2.stop();
        
        logger.info("Waiting for job 2 completion ");
        await().atMost(ONE_MINUTE).until(() -> kieServer1.getLogs(STDOUT).contains("Request job 2 has been completed"));
        
        assertTrue(kieServer1.getLogs(STDOUT).contains("Removing executed job RequestInfo{id=2"));
        
    }

    private Object getJMXAttribute(int jmxPort, String objectName, String attribute) throws Exception {
        JMXConnector jmxConnector = null;
        try {
          @SuppressWarnings("serial")
          Map<String, String[]>   environment = new HashMap<String, String[]>() {{
            put(JMXConnector.CREDENTIALS, new String[] {JMX_USER, JMX_PASSWORD});
          }};
          JMXServiceURL url = new JMXServiceURL(String.format("service:jmx:remote+http://localhost:%s", jmxPort));
          jmxConnector = JMXConnectorFactory.connect(url, environment);
          
          return jmxConnector.getMBeanServerConnection().getAttribute(new ObjectName(objectName), attribute);
        } finally {
          jmxConnector.close();
        }
    }
    
    
    private static void createContainer(KieServicesClient client) {
        ReleaseId releaseId = new ReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        KieContainerResource resource = new KieContainerResource(containerId, releaseId);
        resource.setContainerAlias(ARTIFACT_ID + ALIAS);
        client.createContainer(containerId, resource);
    }

    private static KieServicesClient authenticate(int port, String user, String password) {
        String serverUrl = String.format("http://localhost:%s/kie-server/services/rest/server", port);
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(serverUrl, user, password);
        
        configuration.setTimeout(60000);
        configuration.setMarshallingFormat(MarshallingFormat.JSON);
        return  KieServicesFactory.newKieServicesClient(configuration);
    }

    private Long startProcessInNode2(String processName, int delay) {
        Long processInstanceId = processClient2.startProcess(containerId, processName, singletonMap("delay", delay));
        assertNotNull(processInstanceId);
        return processInstanceId;
    }
}

