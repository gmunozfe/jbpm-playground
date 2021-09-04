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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.appformer.maven.integration.MavenRepository;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.Message;
import org.kie.scanner.KieMavenRepository;
import org.kie.server.services.api.KieServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.google.common.io.Files;

import static org.kie.scanner.KieMavenRepository.getKieMavenRepository;


@SpringBootApplication
public class KieServerApplication {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(KieServerApplication.class);
    
    
    public static void main(String[] args) {
        SpringApplication.run(KieServerApplication.class, args);
    }
      
    @Bean
    CommandLineRunner deployAndValidate() {
        return new CommandLineRunner() {
            
            @Autowired            
            private KieServer kieServer;
            
            @Override
            public void run(String... strings) throws Exception {                
                LOGGER.info("KieServer {} started", kieServer);
                
            }
            
        };
    }
    
    
}
