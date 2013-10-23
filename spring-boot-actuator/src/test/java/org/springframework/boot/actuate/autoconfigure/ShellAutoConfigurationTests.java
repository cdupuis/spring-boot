/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.autoconfigure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.crsh.auth.AuthenticationPlugin;
import org.crsh.plugin.PluginContext;
import org.crsh.plugin.PluginLifeCycle;
import org.crsh.plugin.ResourceKind;
import org.crsh.vfs.Resource;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * Tests for {@link ShellAutoConfiguration}.
 * 
 * @author Christian Dupuis
 */
public class ShellAutoConfigurationTests {

	private AnnotationConfigWebApplicationContext context;

	@Test
	public void testShellConfiguration() throws Exception {
		this.context = new AnnotationConfigWebApplicationContext();
		this.context.register(ShellAutoConfiguration.class);
		this.context.refresh();

		PluginLifeCycle lifeCycle = this.context.getBean(PluginLifeCycle.class);
		assertNotNull(lifeCycle);
		
		assertEquals(lifeCycle.getConfig().getProperty("crash.ssh.port"), "2000");
		assertEquals(lifeCycle.getConfig().getProperty("crash.telnet.port"), "5000");
		
	}
	
	@Test
	public void testCommandResolution() {
		this.context = new AnnotationConfigWebApplicationContext();
		this.context.register(ShellAutoConfiguration.class);
		this.context.refresh();

		PluginLifeCycle lifeCycle = this.context.getBean(PluginLifeCycle.class);
		
		int count = 0;
		for (Resource resource : lifeCycle.getContext().loadResources("login", ResourceKind.LIFECYCLE)) {
			count++;
		}
		assertEquals(1, count);

		count = 0;
		for (Resource resource : lifeCycle.getContext().loadResources("help.java", ResourceKind.COMMAND)) {
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	public void testAuthenticationProvider() {
		this.context = new AnnotationConfigWebApplicationContext(); 
		this.context.setServletContext(new MockServletContext());
		this.context.register(SecurityAutoConfiguration.class,
				EndpointAutoConfiguration.class,
				ManagementServerPropertiesAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class);
		this.context.register(ShellAutoConfiguration.class);
		this.context.refresh();

		PluginLifeCycle lifeCycle = this.context.getBean(PluginLifeCycle.class);
		PluginContext pluginContext = lifeCycle.getContext();

		int count = 0;
		for (AuthenticationPlugin plugin : pluginContext.getPlugins(AuthenticationPlugin.class)) {
			count++;
		}
		assertEquals(3, count);
	}

}
