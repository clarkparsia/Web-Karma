/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.controller.command.service;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.JSONInputCommandFactory;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.util.CommandInputJSONUtil;
import edu.isi.karma.webserver.KarmaException;
import org.json.JSONArray;
import org.json.JSONException;

import javax.servlet.http.HttpServletRequest;

public class PopulateCommandFactory extends JSONInputCommandFactory {
	
	public enum Arguments {
		worksheetId
	}
	
	@Override
	public Command createCommand(HttpServletRequest request,
			Workspace workspace) {

		String worksheetId =request.getParameter(Arguments.worksheetId.name());
		return new PopulateCommand(getNewId(workspace), 
				worksheetId);
	}

	@Override
	public Command createCommand(JSONArray inputJson, Workspace workspace)
			throws JSONException, KarmaException {
		String worksheetId = CommandInputJSONUtil.getStringValue(Arguments.worksheetId.name()
				, inputJson);
		return new PopulateCommand(getNewId(workspace), 
				worksheetId);
	}

	@Override
	public Class<? extends Command> getCorrespondingCommand()
	{
		return PopulateCommand.class;
	}
}
