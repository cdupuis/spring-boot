/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.boot.cli.command;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Christian Dupuis
 */
public class AnnotatedCommandHandlerTests {

	private Command command;
	private TestCommand testCommand;

	@Before
	public void setup() {
		this.testCommand = new TestCommand();
		this.command = AnnotatedCommandHandler.forObject(this.testCommand);
	}

	@Test
	public void testCommandWithAllArguments() throws Exception {
		this.command.run("--test", "Spring Boot", "--isTest");
		assertEquals("Hello from Spring Boot", this.testCommand.getGreeting());
	}

	@Test
	public void testCommandWithOneDefaultArgument() throws Exception {
		this.command.run("--test", "Spring Boot");
		assertEquals("Hello from Spring Boot", this.testCommand.getGreeting());
	}

	@Test
	public void testHelp() throws Exception {
		String help = this.command.getHelp();
		assertTrue(help.contains("--test"));
		assertTrue(help.contains("--isTest"));
	}

	@CliCommand(name = "test", description = "This is a new test command")
	public static class TestCommand {

		private String greeting;

		public void run(
				@CliParameter(name = "test", description = "The test parameter") String test,
				@CliParameter(name = "isTest", defaultValue = "true", description = "The isTest parameter") boolean isTest) {
			if (isTest) {
				this.greeting = "Hello from " + test;
			}
		}

		public String getGreeting() {
			return this.greeting;
		}

	}

}
