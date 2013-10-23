package org.springframework.boot.actuate.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.crsh.auth.AuthenticationPlugin;
import org.crsh.plugin.CRaSHPlugin;
import org.crsh.plugin.PluginLifeCycle;
import org.crsh.spring.SpringBootstrap;
import org.crsh.vfs.FS;
import org.crsh.vfs.spi.AbstractFSDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.ShellAutoConfiguration.ShellProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

@Configuration
@ConditionalOnClass({ PluginLifeCycle.class })
@EnableConfigurationProperties({ ShellProperties.class })
public class ShellAutoConfiguration {

	@Autowired
	private ShellProperties properties;

	@Bean
	@ConditionalOnMissingBean({ PluginLifeCycle.class })
	public SpringBootstrap shellBootstrap() {
		ResourceLoadingShellBootstrap bs = new ResourceLoadingShellBootstrap();

		Properties props = new Properties();
		if (properties.isEnableSsh()) {
			props.put("crash.ssh.port", properties.getSshPort());
		}
		if (properties.isEnableTelnet()) {
			props.put("crash.telnet.port", properties.getTelnetPort());
		}

		props.put("crash.auth", "spring");
		// props.put("crash.auth.simple.username", "admin");
		// props.put("crash.auth.simple.password", "admin");

		bs.setConfig(props);

		// Make that configurable
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
		return new DelegatingAuthenticationPlugin();
	}

	private static class ResourceLoadingShellBootstrap extends SpringBootstrap {

		@Autowired
		private ResourcePatternResolver resourceLoader;

		@Override
		protected FS createCommandFS() throws IOException, URISyntaxException {
			FS cmdFS = new FS();
			cmdFS.mount(new ResourceLoadingFileSystemDriver(new DirectoryHandle("classpath*:/commands/**", resourceLoader)));
			cmdFS.mount(new ResourceLoadingFileSystemDriver(new DirectoryHandle("classpath*:/crash/commands/**",
					resourceLoader)));
			return cmdFS;
		}

		@Override
		protected FS createConfFS() throws IOException, URISyntaxException {
			FS cmdFS = new FS();
			cmdFS.mount(new ResourceLoadingFileSystemDriver(new DirectoryHandle("classpath*:/crash/*", resourceLoader)));
			return cmdFS;
		}
	}

	@SuppressWarnings("rawtypes")
	private static class DelegatingAuthenticationPlugin extends CRaSHPlugin<AuthenticationPlugin> implements
			AuthenticationPlugin<String> {
		
		@Autowired
		private AuthenticationManager authenticationManager;

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
			try { token = authenticationManager.authenticate(token); }
			catch (AuthenticationException ae) { /** TODO CD audit logging */ }
			return token.isAuthenticated();
		}

		@Override
		public Class<String> getCredentialType() {
			return String.class;
		}
	}

	private static class ResourceLoadingFileSystemDriver extends AbstractFSDriver<ResourceHandle> {

		private ResourceHandle root;

		public ResourceLoadingFileSystemDriver(ResourceHandle handle) {
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

		private boolean enableSsh = true;

		private boolean enableTelnet = true;;

		private String sshPort = "2000";

		private String telnetPort = "5000";

		public boolean isEnableSsh() {
			return enableSsh;
		}

		public void setEnableSsh(boolean enableSsh) {
			this.enableSsh = enableSsh;
		}

		public boolean isEnableTelnet() {
			return enableTelnet;
		}

		public void setEnableTelnet(boolean enableTelnet) {
			this.enableTelnet = enableTelnet;
		}

		public String getSshPort() {
			return sshPort;
		}

		public void setSshPort(String sshPort) {
			this.sshPort = sshPort;
		}

		public String getTelnetPort() {
			return telnetPort;
		}

		public void setTelnetPort(String telnetPort) {
			this.telnetPort = telnetPort;
		}
	}

}
