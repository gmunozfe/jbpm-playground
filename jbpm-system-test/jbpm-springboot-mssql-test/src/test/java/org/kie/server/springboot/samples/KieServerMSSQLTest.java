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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.samples.integration.utils.KieJarBuildHelper;
import org.kie.server.api.KieServerConstants;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.definition.ProcessDefinition;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.UserTaskServicesClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.assertj.core.api.Assertions;
import org.kie.server.api.model.definition.QueryDefinition;
import org.kie.server.api.model.definition.TaskField;
import org.kie.server.api.model.definition.TaskQueryFilterSpec;
import org.kie.server.api.model.instance.JobRequestInstance;
import org.kie.server.api.model.instance.TaskInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.api.util.TaskQueryFilterSpecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {JBPMApplication.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test-mssql.properties")
@DirtiesContext(classMode= AFTER_CLASS)
public class KieServerMSSQLTest {

    private static final Logger logger = LoggerFactory.getLogger(KieServerMSSQLTest.class);
    
    private static final String PROCESS_ID_USERTASK = "definition-project.usertask";   
    private static final String QUERY_NAME = "taskInstancesQuery";
    private static final String TASK_QUERY = "select ti.* from AuditTaskImpl ti";

    static final String PATH = "src/test/resources/kjars/";
    
    static final String ARTIFACT_ID = "evaluation";
    static final String GROUP_ID = "org.kie.server.springboot.samples";
    static final String VERSION = "1.0.0";

    static final String ALIAS = "eval";
    static final String CONTAINER_ID = "evaluation";
    static final String PROCESS_ID = "evaluation";

    static final String user = "john";
    static final String password = "john@pwd1";

    @LocalServerPort
    private int port;

    private KieServicesClient kieServicesClient;

    //static final MSSQLServerContainer<?> MSSQL_CONTAINER;
    static final MySQLContainer<?> MSSQL_CONTAINER;
    
    
    
    static {
        //MSSQL_CONTAINER = new MSSQLServerContainer<>(System.getProperty("org.kie.samples.image.sqlserver","mcr.microsoft.com/mssql/server:2019-latest"))
        //                         .acceptLicense();
        
    	MSSQL_CONTAINER = new MySQLContainer<>("mysql:8.0.24");
        //MSSQL_CONTAINER.withInitScript("sql/sqlserver-springboot-jbpm-schema.sql");
        MSSQL_CONTAINER.start();
    }
    
    @BeforeAll
    public static void generalSetup() {
        logger.info("mssql started at "+MSSQL_CONTAINER.getJdbcUrl());
        
        KieJarBuildHelper.createKieJar(PATH + ARTIFACT_ID);
    }

    @AfterAll
    public static void generalCleanup() {
        System.clearProperty(KieServerConstants.KIE_SERVER_MODE);
        MSSQL_CONTAINER.stop();
    }

    @DynamicPropertySource
    public static void registerDbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MSSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.password", MSSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.username", MSSQL_CONTAINER::getUsername);
    }
    
    @BeforeEach
    public void setup() {
        ReleaseId releaseId = new ReleaseId(GROUP_ID, ARTIFACT_ID, VERSION);
        
        String serverUrl = "http://localhost:" + port + "/rest/server";
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(serverUrl, user, password);
        configuration.setTimeout(60000);
        configuration.setMarshallingFormat(MarshallingFormat.JSON);
        this.kieServicesClient = KieServicesFactory.newKieServicesClient(configuration);

        KieContainerResource resource = new KieContainerResource(CONTAINER_ID, releaseId);
        resource.setContainerAlias(ALIAS);
        kieServicesClient.createContainer(CONTAINER_ID, resource);
    
        QueryDefinition query = new QueryDefinition();
        query.setName(QUERY_NAME);
        query.setSource(System.getProperty("org.kie.server.persistence.ds", "jdbc/jbpm-ds"));
        query.setExpression(TASK_QUERY);
        query.setTarget("CUSTOM");
        
        QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        queryClient.registerQuery(query);
    }

        
    @AfterEach
    public void cleanup() {
    	QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
    	queryClient.unregisterQuery(QUERY_NAME);
        if (kieServicesClient != null) {
            ServiceResponse<Void> response = kieServicesClient.disposeContainer(CONTAINER_ID);
            logger.info("Container {} disposed with response - {}", CONTAINER_ID, response.getMsg());
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
    
    @Test
    public void testFindTaskWithNameEqualsToFilter() throws Exception {
    	UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
    	ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
        Long processInstanceId = processClient.startProcess( CONTAINER_ID, "definition-project.usertask" );
        Assertions.assertThat( processInstanceId ).isNotNull();

       /* List<TaskSummary> tasks = taskClient.findTasksAssignedAsPotentialOwner( "yoda",
                                                                                0,
                                                                                10 );*/
        
        List<TaskSummary> tasks = taskClient.findTasksByStatusByProcessInstanceId(processInstanceId, null, 0, 10);

        System.out.println("\t######### Tasks: " +tasks);
        
        Assertions.assertThat( tasks ).isNotEmpty();
        TaskSummary task = tasks.get( 0 );
        testFindTaskInstanceWithSearchService( createQueryFilterEqualsTo( TaskField.NAME,
        		                                                          "First task" ),
                                               task.getId() );
    }               
    
    private void testFindTaskInstanceWithSearchService( TaskQueryFilterSpec filter,
            Long taskInstanceId ) {
            List<Long> resultsIds = new ArrayList<>();
            QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        	
            List<TaskInstance> results = queryClient.findHumanTasksWithFilters( QUERY_NAME, 
                                            filter,
                                            0,
                                            100 );
            for ( TaskInstance res : results ) {
            resultsIds.add( res.getId() );
            
            Assertions.assertThat(res.getInputData()).isNullOrEmpty();
            Assertions.assertThat(res.getOutputData()).isNullOrEmpty();
            }
            
            Assertions.assertThat( results ).isNotNull();
            Assertions.assertThat( results ).isNotEmpty();
            
            System.out.println("@@@ resultsIds: "+resultsIds);
            Assertions.assertThat( resultsIds ).contains( taskInstanceId );
            }
            
    private TaskQueryFilterSpec createQueryFilterEqualsTo( TaskField taskField,
                           Comparable<?> equalsTo ) {
            return new TaskQueryFilterSpecBuilder().equalsTo( taskField,
                          equalsTo ).get();
    }
            
    private TaskQueryFilterSpec createQueryFilterGreaterThan( TaskField taskField,
                              Comparable<?> greaterThan ) {
            return new TaskQueryFilterSpecBuilder().greaterThan( taskField,
                             greaterThan ).get();
    }
            
    private TaskQueryFilterSpec createQueryFilterGreaterThanAndEqualsTo( TaskField taskField,
                                         Comparable<?> greaterThan,
                                         Comparable<?> equalsTo ) {
            return new TaskQueryFilterSpecBuilder().greaterThan( taskField,
                             greaterThan ).equalsTo( taskField,
                                                     equalsTo ).get();
    }
            
    private TaskQueryFilterSpec createQueryFilterGreaterThanOrEqualsTo( TaskField taskField,
                                        Comparable<?> equalsTo ) {
            return new TaskQueryFilterSpecBuilder().greaterOrEqualTo( taskField,
                                  equalsTo ).get();
            }
            
            private TaskQueryFilterSpec createQueryFilterAndEqualsTo( Map<TaskField, Comparable<?>> filterProperties ) {
            TaskQueryFilterSpecBuilder result = new TaskQueryFilterSpecBuilder();
            filterProperties.forEach( result::equalsTo );
            return result.get();
    }

}

