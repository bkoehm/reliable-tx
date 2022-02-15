/*-
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  The
 * ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.github.bkoehm.reliabletx.camel;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import junit.framework.TestCase;

/**
 * @author Brian Koehmstedt
 */
public abstract class SpringTestCase extends TestCase {
    private ClassPathXmlApplicationContext springContext;

    public void setUpSpring(String springResourcesFile) {
        /* Initialize a Spring context using an application context
         * configured from a config file that configures a JTA transaction
         * manager. */
        this.springContext = new ClassPathXmlApplicationContext(springResourcesFile);
    }

    public ApplicationContext getSpringContext() {
        assertNotNull(springContext);
        return springContext;
    }

    public void stopSpringContext() {
        assertNotNull(springContext);
        try {
            springContext.close();
        } finally {
            springContext.stop();
        }
    }

    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        assertNotNull(springContext);
        return springContext.getBean(name, requiredType);
    }
}
