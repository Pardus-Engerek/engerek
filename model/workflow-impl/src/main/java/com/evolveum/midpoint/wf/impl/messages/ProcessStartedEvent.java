/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.wf.impl.messages;

import com.evolveum.midpoint.wf.impl.processes.ProcessInterfaceFinder;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: mederly
 * Date: 28.7.2012
 * Time: 16:28
 * To change this template use File | Settings | File Templates.
 */
public class ProcessStartedEvent extends ProcessEvent {

	public ProcessStartedEvent(String pid, Map<String, Object> variables, ProcessInterfaceFinder processInterfaceFinder) {
		super(pid, variables, processInterfaceFinder);
	}
}
