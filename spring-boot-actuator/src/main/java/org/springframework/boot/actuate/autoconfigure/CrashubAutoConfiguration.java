package org.springframework.boot.actuate.autoconfigure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.crsh.spring.SpringBootstrap;
import org.crsh.util.InputStreamFactory;
import org.crsh.util.ZipIterator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({ SpringBootstrap.class })
public class CrashubAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SpringBootstrap crashubBootstrap() {
		SpringBootstrap bs = new SpringBootstrap();

		Properties props = new Properties();
		props.put("crash.ssh.port", "2000");
		props.put("crash.telnet.port", "5000");

		props.put("crash.auth", "simple");
		props.put("crash.auth.simple.username", "admin");
		props.put("crash.auth.simple.password", "admin");

		bs.setConfig(props);

		return bs;
	}

	public static void main(String[] args) throws IOException, URISyntaxException {
		//File f = new File(
		//		"/Users/cdupuis/Development/Java/work/spring-boot/git/spring-boot-samples/spring-boot-sample-actuator/target/spring-boot-sample-actuator-0.5.0.BUILD-SNAPSHOT.jar!/lib/crash.shell-1.3.0-beta8.jar");
		//create(f);
		
		ZipIterator.create(new URL("jar:file:/Users/cdupuis/Development/Java/work/spring-boot/git/spring-boot-samples/spring-boot-sample-actuator/target/spring-boot-sample-actuator-0.5.0.BUILD-SNAPSHOT.jar!/lib/crash.shell-1.3.0-beta8.jar"));
	}

	static ZipIterator create(File file) throws IOException {
		int ix = file.getAbsolutePath().indexOf("!/");
		if (ix > 0) {
			File f = new File(file.getAbsolutePath().substring(0, ix));
			ZipFile jarFile = new ZipFile(file);
			Enumeration<? extends ZipEntry> en = jarFile.entries();
			
			
			
			
			return null;
		}	
		else {
			// The fast way (but that requires a File object)
			final ZipFile jarFile = new ZipFile(file);
			final Enumeration<? extends ZipEntry> en = jarFile.entries();
			en.hasMoreElements();
			return new ZipIterator() {
				ZipEntry next;

				@Override
				public boolean hasNext() throws IOException {
					return en.hasMoreElements();
				}

				@Override
				public ZipEntry next() throws IOException {
					return next = en.nextElement();
				}

				public void close() throws IOException {
				}

				@Override
				public InputStreamFactory open() throws IOException {
					final ZipEntry capture = next;
					return new InputStreamFactory() {
						public InputStream open() throws IOException {
							return jarFile.getInputStream(capture);
						}
					};
				}
			};
		}
	}

}
