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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.crsh.auth.AuthenticationPlugin;
import org.crsh.plugin.CRaSHPlugin;
import org.crsh.plugin.PluginContext;
import org.crsh.plugin.PluginDiscovery;
import org.crsh.plugin.PluginLifeCycle;
import org.crsh.plugin.PropertyDescriptor;
import org.crsh.plugin.ServiceLoaderDiscovery;
import org.crsh.vfs.FS;
import org.crsh.vfs.spi.AbstractFSDriver;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.ShellAutoConfiguration.ShellProperties;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.SecurityConfig;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for embedded a extensible shell into an application.
 * 
 * TODO CD add documentation
 * 
 * @author Christian Dupuis
 */
@Configuration
@ConditionalOnClass({ PluginLifeCycle.class })
@EnableConfigurationProperties({ ShellProperties.class })
public class ShellAutoConfiguration {

	@Autowired
	private ShellProperties properties;
	
	@Bean
	@ConditionalOnExpression("#{environment['shell.auth'] == 'jaas'}")
	@ConditionalOnMissingBean({ AuthenticationProperties.class })
	public AuthenticationProperties jaasAuthenticationProperties() {
		return new JaasAuthenticationProperties();
	}

	@Bean
	@ConditionalOnExpression("#{environment['shell.auth'] == 'key'}")
	@ConditionalOnMissingBean({ AuthenticationProperties.class })
	public AuthenticationProperties keyAuthenticationProperties() {
		return new KeyAuthenticationProperties();
	}

	@Bean
	@ConditionalOnExpression("#{environment['shell.auth'] == 'simple'}")
	@ConditionalOnMissingBean({ AuthenticationProperties.class })
	public AuthenticationProperties simpleAuthenticationProperties() {
		return new SimpleAuthenticationProperties();
	}

	@Bean
	@ConditionalOnExpression("#{environment['shell.auth'] == 'spring'}")
	@ConditionalOnMissingBean({ AuthenticationProperties.class })
	public AuthenticationProperties SpringAuthenticationProperties() {
		return new SpringAuthenticationProperties();
	}

	@Bean
	@ConditionalOnMissingBean({ PluginLifeCycle.class })
	public PluginLifeCycle shellBootstrap() {
		ShellBootstrap bs = new ShellBootstrap();
		bs.setConfig(properties.mergeProperties(new Properties()));

		// TODO CD Make that configurable
		// Logger log = Logger.getLogger("org.crsh");
		// log.setLevel(Level.WARNING);
		//
		// log = Logger.getLogger("net.wimpi.telnetd.net");
		// log.setLevel(Level.WARNING);

		return bs;
	}

	@Bean
	@ConditionalOnBean({ AuthenticationManager.class })
	@SuppressWarnings("rawtypes")
	public CRaSHPlugin<AuthenticationPlugin> shellAuthenticationManager() {
		return new AuthenticationManagerAdapter();
	}

	private static class ShellBootstrap extends PluginLifeCycle {
		
		@Autowired
		private ShellProperties properties;
		
		@Autowired
		private ResourcePatternResolver resourceLoader;

		@Autowired
		private ListableBeanFactory beanFactory;

		@PostConstruct
		public void init() throws Exception {
			Map<String, Object> attributes = new HashMap<String, Object>();

			// TODO CD that shouldn't be here
			String bootVersion = ShellAutoConfiguration.class.getPackage().getImplementationVersion();
			if (bootVersion != null)
				attributes.put("spring.boot.version", bootVersion);

			FS commandFileSystem = createFileSystem(properties.getCommandPathPatterns());
			FS confFileSystem = createFileSystem(properties.getConfigPathPatterns());

			PluginDiscovery discovery = new BeanFactoryFilteringPluginDiscovery(resourceLoader.getClassLoader(),
					beanFactory);

			PluginContext context = new PluginContext(discovery, attributes, commandFileSystem, confFileSystem,
					resourceLoader.getClassLoader());

			context.refresh();

			start(context);
		}

		@PreDestroy
		public void destroy() {
			stop();
		}

		protected FS createFileSystem(String[] pathPatterns) throws IOException, URISyntaxException {
			Assert.notNull(pathPatterns);
			FS cmdFS = new FS();
			for (String pathPattern : pathPatterns) {
				cmdFS.mount(new SimpleFileSystemDriver(new DirectoryHandle(pathPattern, resourceLoader)));
			}
			return cmdFS;
		}
	}

	private static class BeanFactoryFilteringPluginDiscovery extends ServiceLoaderDiscovery {

		private ListableBeanFactory beanFactory;

		public BeanFactoryFilteringPluginDiscovery(ClassLoader classLoader, ListableBeanFactory beanFactory)
				throws NullPointerException {
			super(classLoader);
			this.beanFactory = beanFactory;
		}

		@Override
		public Iterable<CRaSHPlugin<?>> getPlugins() {
			List<CRaSHPlugin<?>> plugins = new ArrayList<CRaSHPlugin<?>>();

			// TODO CD add filtering of installed plugins based on configuration
			for (CRaSHPlugin<?> p : super.getPlugins()) {
				plugins.add(p);
			}

			Collection<CRaSHPlugin> springPlugins = beanFactory.getBeansOfType(CRaSHPlugin.class).values();
			for (CRaSHPlugin p : springPlugins) {
				plugins.add(p);
			}

			return plugins;
		}
	}
	
	@SuppressWarnings("rawtypes")
	private static class AuthenticationManagerAdapter extends CRaSHPlugin<AuthenticationPlugin> implements
			AuthenticationPlugin<String> {
		
		public static final PropertyDescriptor<String> ROLES = PropertyDescriptor.create(
				"auth.spring.roles", "ADMIN", "Comma separated list of roles required to access the shell");
		
		private String[] roles = new String[] { "ADMIN" };
		
		@Autowired
		private AuthenticationManager authenticationManager;
		
		@Autowired(required=false)
		private AccessDecisionManager accessDecisionManager;
		
		@Override
		public AuthenticationPlugin<String> getImplementation() {
			return this;
		}

		@Override
		public String getName() {
			return "spring";
		}

		@Override
		public boolean authenticate(String username, String password) throws Exception {
			Authentication token = new UsernamePasswordAuthenticationToken(username, password);
			try {
				token = authenticationManager.authenticate(token);
			}
			catch (AuthenticationException ae) {}

			// TODO CD not sure the following works. add tests
			if (accessDecisionManager != null && token.isAuthenticated() && roles != null && roles.length > 0) {
				try {
					accessDecisionManager.decide(token, this, SecurityConfig.createList(roles));
				}
				catch (AccessDeniedException e) {
					return false;
				}
			}
			return token.isAuthenticated();
		}
		
		@Override
		public void init() {
			String rolesPropertyValue = getContext().getProperty(ROLES);
			if (rolesPropertyValue != null) {
				this.roles = StringUtils.commaDelimitedListToStringArray(rolesPropertyValue);
			}
		}

		@Override
		public Class<String> getCredentialType() {
			return String.class;
		}

		@Override
		protected Iterable<PropertyDescriptor<?>> createConfigurationCapabilities() {
			return Arrays.<PropertyDescriptor<?>>asList(ROLES);
		}
	}

	private static class SimpleFileSystemDriver extends AbstractFSDriver<ResourceHandle> {

		private ResourceHandle root;

		public SimpleFileSystemDriver(ResourceHandle handle) {
			this.root = handle;
		}

		@Override
		public ResourceHandle root() throws IOException {
			return root;
		}

		@Override
		public String name(ResourceHandle handle) throws IOException {
			return handle.getName();
		}

		@Override
		public boolean isDir(ResourceHandle handle) throws IOException {
			return handle instanceof DirectoryHandle;
		}

		@Override
		public Iterable<ResourceHandle> children(ResourceHandle handle) throws IOException {
			if (handle instanceof DirectoryHandle) {
				return ((DirectoryHandle) handle).members();
			}
			return Collections.emptySet();
		}

		@Override
		public long getLastModified(ResourceHandle handle) throws IOException {
			return -1;
		}

		@Override
		public Iterator<InputStream> open(ResourceHandle handle) throws IOException {
			if (handle instanceof FileHandle) {
				return Collections.singletonList(((FileHandle) handle).openStream()).iterator();
			}
			return Collections.emptyIterator();
		}

	}

	private abstract static class ResourceHandle {

		private String name;

		public ResourceHandle(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	private static class FileHandle extends ResourceHandle {

		private Resource resource;

		public FileHandle(String name, Resource resource) {
			super(name);
			this.resource = resource;
		}

		public InputStream openStream() throws IOException {
			return this.resource.getInputStream();
		}
	}

	private static class DirectoryHandle extends ResourceHandle {

		private ResourcePatternResolver resourceLoader;

		public DirectoryHandle(String name, ResourcePatternResolver resourceLoader) {
			super(name);
			this.resourceLoader = resourceLoader;
		}

		public List<ResourceHandle> members() throws IOException {
			Resource[] resources = resourceLoader.getResources(getName());
			List<ResourceHandle> files = new ArrayList<ResourceHandle>();
			for (Resource resource : resources) {
				if (!resource.getURL().getPath().endsWith("/")) {
					files.add(new FileHandle(resource.getFilename(), resource));
				}
			}
			return files;
		}

	}

	@ConfigurationProperties(name = "shell", ignoreUnknownFields = false)
	public static class ShellProperties {

		@Autowired(required=false)
		private AuthenticationProperties authenticationProperties;
		
		private Ssh ssh = new Ssh();

		private Telnet telnet = new Telnet();

		private String[] commandPathPatterns = new String[] { "classpath*:/commands/**",
			"classpath*:/crash/commands/**" };

		private String[] configPathPatterns = new String[] { "classpath*:/crash/*" };

		private String auth = "simple";
		

		public void setSsh(Ssh ssh) {
			this.ssh = ssh;
		}

		public void setTelnet(Telnet telnet) {
			this.telnet = telnet;
		}

		public void setCommandPathPatterns(String[] commandPathPatterns) {
			this.commandPathPatterns = commandPathPatterns;
		}

		public void setConfigPathPatterns(String[] configPathPatterns) {
			this.configPathPatterns = configPathPatterns;
		}
		
		public void setAuth(String auth) {
			this.auth = auth;
		}
		
		public Ssh getSsh() {
			return ssh;
		}

		public Telnet getTelnet() {
			return telnet;
		}

		public AuthenticationProperties getAuthenticationProperties() {
			return authenticationProperties;
		}

		public void setAuthenticationProperties(AuthenticationProperties authenticationProperties) {
			this.authenticationProperties = authenticationProperties;
		}

		public String[] getCommandPathPatterns() {
			return commandPathPatterns;
		}

		public String[] getConfigPathPatterns() {
			return configPathPatterns;
		}

		public String getAuth() {
			return auth;
		}

		public Properties mergeProperties(Properties properties) {
			if (ssh != null) {
				properties = ssh.mergeProperties(properties);
			}
			if (telnet != null) {
				properties = telnet.mergeProperties(properties);
			}
			
			properties.put("crash.auth", auth);
			if (authenticationProperties != null) {
				properties = authenticationProperties.mergeProperties(properties);
			}

			return properties;
		}

		public static class Ssh implements PropertiesProvider {

			private boolean enabled = true;

			private String port = "2000";

			private String keyPath = null;

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

			public void setPort(String port) {
				this.port = port;
			}

			public void setKeyPath(String keyPath) {
				this.keyPath = keyPath;
			}

			@Override
			public Properties mergeProperties(Properties properties) {
				if (this.enabled) {
					properties.put("crash.ssh.port", this.port);
					if (this.keyPath != null) {
						properties.put("crash.ssh.keypath", this.keyPath);
					}
				}
				return properties;
			}
		}

		public static class Telnet implements PropertiesProvider {

			private boolean enabled = true;

			private String port = "5000";

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

			public void setPort(String port) {
				this.port = port;
			}

			@Override
			public Properties mergeProperties(Properties properties) {
				if (this.enabled) properties.put("crash.telnet.port", this.port);
				return properties;
			}
		}

	}
	
	public interface PropertiesProvider {
		Properties mergeProperties(Properties properties);
	}
	
	public interface AuthenticationProperties extends PropertiesProvider {}
	
	@ConfigurationProperties(name = "shell.auth.jaas", ignoreUnknownFields = false)
	public static class JaasAuthenticationProperties implements AuthenticationProperties {

		private String domain = "my-domain";

		public void setDomain(String domain) {
			this.domain = domain;
		}

		@Override
		public Properties mergeProperties(Properties properties) {
			properties.put("crash.auth.jaas.domain", domain);
			return properties;
		}
	}

	@ConfigurationProperties(name = "shell.auth.spring", ignoreUnknownFields = false)
	public static class SpringAuthenticationProperties implements AuthenticationProperties {

		private String[] roles = new String[] { "ADMIN" };

		public void setRoles(String[] roles) {
			this.roles = roles;
		}
		
		@Override
		public Properties mergeProperties(Properties properties) {
			properties.put("crash.auth.spring.roles", StringUtils.arrayToCommaDelimitedString(roles));
			return properties;
		}
	}

	@ConfigurationProperties(name = "shell.auth.simple", ignoreUnknownFields = false)
	public static class SimpleAuthenticationProperties implements AuthenticationProperties {
		
		private String username;
		
		private String password;
		
		public void setUsername(String username) {
			this.username = username;
		}

		public void setPassword(String password) {
			this.password = password;
		}
		
		@Override
		public Properties mergeProperties(Properties properties) {
			properties.put("crash.auth.simple.username", username);
			properties.put("crash.auth.simple.password", password);
			return properties;
		}
	}

	@ConfigurationProperties(name = "shell.auth.key", ignoreUnknownFields = false)
	public static class KeyAuthenticationProperties implements AuthenticationProperties {
		
		private String path;
		
		public void setPath(String path) {
			this.path = path;
		}
		
		@Override
		public Properties mergeProperties(Properties properties) {
			properties.put("crash.auth.key.path", path);
			return properties;
		}
	}

}
