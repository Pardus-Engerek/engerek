/*
 * Copyright (c) 2010-2015 Evolveum
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

package com.evolveum.midpoint.util.exception;

/**
 * Specific kind of SchemaException. Used e.g. to treat "no name" problems in previewChanges method nicely.
 * SchemaException.propertyName:=UserType.F_NAME could be used as well, but it's a bit ambiguous.
 * 
 * A little bit experimental. (We certainly don't want to have millions of exception types.)
 * 
 * @author mederly
 */
public class NoFocusNameSchemaException extends SchemaException {

    public NoFocusNameSchemaException(String message) {
        super(message);
    }
}
