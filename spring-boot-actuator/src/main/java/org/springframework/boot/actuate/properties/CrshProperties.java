/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.boot.actuate.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 *
 * 
 * @author Christian Dupuis
 */
@ConfigurationProperties(name = "shell", ignoreUnknownFields = true)
public class CrshProperties {
	
	protected static final String CRASH_AUTH = "crash.auth";
	protected static final String CRASH_AUTH_JAAS_DOMAIN = "crash.auth.jaas.domain";
	protected static final String CRASH_AUTH_KEY_PATH = "crash.auth.key.path";
	protected static final String CRASH_AUTH_SIMPLE_PASSWORD = "crash.auth.simple.password";
	protected static final String CRASH_AUTH_SIMPLE_USERNAME = "crash.auth.simple.username";
	protected static final String CRASH_AUTH_SPRING_ROLES = "crash.auth.spring.roles";
	protected static final String CRASH_SSH_KEYPATH = "crash.ssh.keypath";
	protected static final String CRASH_SSH_PORT = "crash.ssh.port";
	protected static final String CRASH_TELNET_PORT = "crash.telnet.port";
	protected static final String CRASH_VFS_REFRESH_PERIOD = "crash.vfs.refresh_period";

	private String auth = "simple";
	
	private String commandRefreshInterval = null;

	@Autowired(required = false)
	private AuthenticationProperties authenticationProperties;

	private String[] commandPathPatterns = new String[] { "classpath*:/commands/**", 
			"classpath*:/crash/commands/**" };

	private String[] configPathPatterns = new String[] { "classpath*:/crash/*" };

	private String[] disabledPlugins = new String[0];

	private Ssh ssh = new Ssh();

	private Telnet telnet = new Telnet();


	public String getAuth() {
		return this.auth;
	}

	public AuthenticationProperties getAuthenticationProperties() {
		return this.authenticationProperties;
	}
	
	public String getCommandRefreshInterval() {
		return this.commandRefreshInterval;
	}
	
	public String[] getCommandPathPatterns() {
		return this.commandPathPatterns;
	}

	public String[] getConfigPathPatterns() {
		return this.configPathPatterns;
	}

	public String[] getDisabledPlugins() {
		return this.disabledPlugins;
	}

	public Ssh getSsh() {
		return this.ssh;
	}

	public Telnet getTelnet() {
		return this.telnet;
	}

	public Properties mergeProperties(Properties properties) {
		if (ssh != null) {
			properties = ssh.mergeProperties(properties);
		}
		if (telnet != null) {
			properties = telnet.mergeProperties(properties);
		}

		properties.put(CRASH_AUTH, auth);
		if (authenticationProperties != null) {
			properties = authenticationProperties.mergeProperties(properties);
		}
		
		if (StringUtils.hasText(this.commandRefreshInterval)) {
			properties.put(CRASH_VFS_REFRESH_PERIOD, this.commandRefreshInterval);
		}
		
		// special handling for disabling Ssh and Telnet support
		List<String> dp = new ArrayList<String>(Arrays.asList(this.disabledPlugins)); 
		if (!ssh.isEnabled()) {
			dp.add("org.crsh.ssh.SSHPlugin");
		}
		if (!telnet.isEnabled()) {
			dp.add("org.crsh.telnet.TelnetPlugin");
		}
		this.disabledPlugins = dp.toArray(new String[dp.size()]);
		
		return properties;
	}

	public void setAuth(String auth) {
		this.auth = auth;
	}

	public void setAuthenticationProperties(AuthenticationProperties authenticationProperties) {
		this.authenticationProperties = authenticationProperties;
	}
	
	public void setCommandRefreshInterval(String commandRefreshInterval) {
		this.commandRefreshInterval = commandRefreshInterval;
	}

	public void setCommandPathPatterns(String[] commandPathPatterns) {
		this.commandPathPatterns = commandPathPatterns;
	}

	public void setConfigPathPatterns(String[] configPathPatterns) {
		this.configPathPatterns = configPathPatterns;
	}

	public void setDisabledPlugins(String[] disabledPlugins) {
		this.disabledPlugins = disabledPlugins;
	}

	public void setSsh(Ssh ssh) {
		this.ssh = ssh;
	}

	public void setTelnet(Telnet telnet) {
		this.telnet = telnet;
	}
	
	
	public interface AuthenticationProperties extends PropertiesProvider {
	}
	
	
	@ConfigurationProperties(name = "shell.auth.jaas", ignoreUnknownFields = false)
	public static class JaasAuthenticationProperties implements AuthenticationProperties {

		private String domain = "my-domain";

		
		@Override
		public Properties mergeProperties(Properties properties) {
			if (this.domain != null) {
				properties.put(CRASH_AUTH_JAAS_DOMAIN, this.domain);
			}
			return properties;
		}

		public void setDomain(String domain) {
			this.domain = domain;
		}

	}
	

	@ConfigurationProperties(name = "shell.auth.key", ignoreUnknownFields = false)
	public static class KeyAuthenticationProperties implements AuthenticationProperties {

		private String path;

		
		@Override
		public Properties mergeProperties(Properties properties) {
			if (this.path != null) {
				properties.put(CRASH_AUTH_KEY_PATH, this.path);
			}
			return properties;
		}

		public void setPath(String path) {
			this.path = path;
		}

	}

	
	public interface PropertiesProvider {
		Properties mergeProperties(Properties properties);
	}

	
	@ConfigurationProperties(name = "shell.auth.simple", ignoreUnknownFields = false)
	public static class SimpleAuthenticationProperties implements AuthenticationProperties {

		private String password;

		private String username;

		
		@Override
		public Properties mergeProperties(Properties properties) {
			if (this.username != null) {
				properties.put(CRASH_AUTH_SIMPLE_USERNAME, this.username);
			}
			if (this.password != null) {
				properties.put(CRASH_AUTH_SIMPLE_PASSWORD, this.password);
			}
			return properties;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public void setUsername(String username) {
			this.username = username;
		}

	}


	@ConfigurationProperties(name = "shell.auth.spring", ignoreUnknownFields = false)
	public static class SpringAuthenticationProperties implements AuthenticationProperties {

		private String[] roles = new String[] { "ROLE_ADMIN" };

		
		@Override
		public Properties mergeProperties(Properties properties) {
			if (this.roles != null) {
				properties.put(CRASH_AUTH_SPRING_ROLES, StringUtils.arrayToCommaDelimitedString(this.roles));
			}
			return properties;
		}

		public void setRoles(String[] roles) {
			this.roles = roles;
		}

	}

	
	public static class Ssh implements PropertiesProvider {

		private boolean enabled = true;

		private String keyPath = null;

		private String port = "2000";

		
		public boolean isEnabled() {
			return this.enabled;
		}
		
		@Override
		public Properties mergeProperties(Properties properties) {
			if (this.enabled) {
				if (this.port != null) {
					properties.put(CRASH_SSH_PORT, this.port);
				}
				if (this.keyPath != null) {
					properties.put(CRASH_SSH_KEYPATH, this.keyPath);
				}
			}
			return properties;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public void setKeyPath(String keyPath) {
			this.keyPath = keyPath;
		}

		public void setPort(String port) {
			this.port = port;
		}

	}

	
	public static class Telnet implements PropertiesProvider {

		private boolean enabled = true;

		private String port = "5000";

		
		public boolean isEnabled() {
			return this.enabled;
		}
		
		@Override
		public Properties mergeProperties(Properties properties) {
			if (this.enabled) {
				if (this.port != null) {
					properties.put(CRASH_TELNET_PORT, this.port);
				}
			}
			return properties;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public void setPort(String port) {
			this.port = port;
		}

	}

}
