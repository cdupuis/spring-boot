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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import joptsimple.OptionSet;

import org.springframework.boot.cli.command.options.OptionHandler;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * {@link Command} implementation that allows for <code>run</code> methods that take typed
 * parameters instead of an {@link OptionSet} or the command line args array.
 * 
 * <p>
 * Subclasses need to implement a <code>run</code> method whose method parameters are
 * annotated with {@link CliParameter}.
 * 
 * <p>
 * As {@link Command} represents a single command only one <code>run</code> method per
 * sub-class is allowed.
 * 
 * @author Christian Dupuis
 */
public class AnnotatedCommandHandler extends OptionParsingCommand {

	public AnnotatedCommandHandler(String name, String description, Object command) {
		super(name, description, new AnnotatedOptionHandler());
		((AnnotatedOptionHandler) getHandler()).setCommand(command);
	}

	public static AnnotatedCommandHandler forObject(Object command) {
		CliCommand cliCommand = AnnotationUtils.findAnnotation(command.getClass(),
				CliCommand.class);
		Assert.notNull(cliCommand, "Command object must have @CliCommand annotation");
		return new AnnotatedCommandHandler(cliCommand.name(), cliCommand.description(),
				command);
	}

	private static class AnnotatedOptionHandler extends OptionHandler {

		private ConversionService conversionService = new DefaultConversionService();

		private Object command;

		private Method commandMethod;

		private List<MethodParameter> parameters;

		public void setCommand(Object command) {
			this.command = command;
		}

		@Override
		protected void options() {
			synchronized (this) {
				if (this.parameters == null) {
					extractCommandMethod();
				}
			}

			for (MethodParameter parameter : this.parameters) {
				CliParameter parameterAnnotation = parameter
						.getParameterAnnotation(CliParameter.class);
				option(parameterAnnotation.name(), parameterAnnotation.description())
						.withOptionalArg();
			}
		}

		@Override
		protected void run(OptionSet options) throws Exception {
			synchronized (this) {
				if (this.parameters == null) {
					extractCommandMethod();
				}
			}

			List<Object> parameters = prepareParameters(options);
			ReflectionUtils.invokeMethod(this.commandMethod, this.command,
					parameters.toArray(new Object[parameters.size()]));
		}

		protected void extractCommandMethod() {
			Assert.notNull(this.command, "Command must not be null");
			Method[] methods = this.command.getClass().getDeclaredMethods();

			// TODO what if we find more then one method? => raise error
			for (Method method : methods) {

				// TODO no hard requirement to have this method be called run. better
				// use another annotation?
				if ("run".equals(method.getName())) {
					this.commandMethod = method;
					this.parameters = new ArrayList<MethodParameter>();

					extractParameters(method);
					break;
				}
			}
		}

		protected void extractParameters(Method method) {
			int parameterCount = this.commandMethod.getParameterTypes().length;
			for (int i = 0; i < parameterCount; i++) {
				MethodParameter parameter = new MethodParameter(this.commandMethod, i);
				validateMethodParameter(method, parameter);
				this.parameters.add(parameter);
			}
		}

		protected void validateMethodParameter(Method method, MethodParameter parameter) {
			CliParameter parameterAnnotation = parameter
					.getParameterAnnotation(CliParameter.class);
			Assert.notNull(
					parameterAnnotation,
					"@CliParameter annotation missing on parameter '"
							+ parameter.getParameterName() + "' on method " + method);
		}

		protected List<Object> prepareParameters(OptionSet options) {
			List<Object> parameters = new ArrayList<Object>();

			for (MethodParameter parameter : this.parameters) {
				CliParameter parameterAnnotation = parameter
						.getParameterAnnotation(CliParameter.class);
				String name = parameterAnnotation.name();

				Object obj = null;
				if (options.has(name) && options.hasArgument(name)) {
					obj = options.valueOf(name);
				}
				else if ("__NULL__".equals(parameterAnnotation.defaultValue())) {
					obj = null;
				}
				else if (parameterAnnotation.defaultValue() != null) {
					obj = parameterAnnotation.defaultValue();
				}

				// Do type conversion
				obj = this.conversionService.convert(obj, parameter.getParameterType());

				// Add converted method parameter
				parameters.add(obj);
			}

			return parameters;
		}
	}

}
