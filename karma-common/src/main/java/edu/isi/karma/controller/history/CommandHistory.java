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
/**
 * 
 */
package edu.isi.karma.controller.history;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandType;
import edu.isi.karma.controller.command.ICommand;
import edu.isi.karma.controller.command.ICommand.CommandTag;
import edu.isi.karma.controller.history.HistoryJsonUtil.ClientJsonKeys;
import edu.isi.karma.controller.history.HistoryJsonUtil.ParameterType;
import edu.isi.karma.controller.update.HistoryAddCommandUpdate;
import edu.isi.karma.controller.update.HistoryUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.view.VWorkspace;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.*;

/**
 * @author szekely
 * 
 */
public class CommandHistory {

	private final List<ICommand> history = new ArrayList<ICommand>();

	private final List<ICommand> redoStack = new ArrayList<ICommand>();

	/**
	 * If the last command was undo, and then we do a command that goes on the
	 * history, then we need to send the browser the full history BEFORE we send
	 * the update for the command the user just did. The reason is that the
	 * history may contain undoable commands, and the browser does not know how
	 * to reset the history.
	 */
	private boolean lastCommandWasUndo = false;
	
	/**
	 * Used to keep a pointer to the command which require user-interaction
	 * through multiple HTTP requests.
	 */
	private Command currentCommand;
	
	private final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());

	private static HashMap<String, IHistorySaver> historySavers = new HashMap<>();
	
	public enum HistoryArguments {
		worksheetId, commandName, inputParameters, hNodeId, tags
	}
	
	public CommandHistory() {
	}

	public CommandHistory(List<ICommand> history, List<ICommand> redoStack) {
		for (ICommand c : history) {
			this.history.add(c);
		}
		for (ICommand c : redoStack) {
			this.redoStack.add(c);
		}
	}

	public boolean isUndoEnabled() {
		return !history.isEmpty();
	}

	public boolean isRedoEnabled() {
		return !redoStack.isEmpty();
	}

	public List<ICommand> _getHistory() {
		return history;
	}

	public List<ICommand> _getRedoStack() {
		return redoStack;
	}

	public CommandHistory clone() {
		return new CommandHistory(history, redoStack);
	}
	
	public void setCurrentCommand(Command command) {
		this.currentCommand = command;
	}
	
	public Command getCurrentCommand() {
		return this.currentCommand;
	}

	/**
	 * Commands go onto the history when they have all their arguments and are
	 * ready to be executed. If a command needs multiple user interactions to
	 * define the parameters, then the command object must be created,
	 * interacted with and then executed. 
	 * 
	 * @param command
	 * @param workspace
	 * @return UpdateContainer with all the changes done by the command.
	 * @throws CommandException
	 */
	public UpdateContainer doCommand(Command command, Workspace workspace)
			throws CommandException {
		UpdateContainer effects = new UpdateContainer();
		effects.append(command.doIt(workspace));
		command.setExecuted(true);
		
		
		if (command.getCommandType() != CommandType.notInHistory) {
			redoStack.clear();
			
			// Send the history before the command we just executed.
			if (lastCommandWasUndo && !(instanceOf(command, "UndoRedoCommand"))) {
				effects.append(new UpdateContainer(new HistoryUpdate(this)));
			}
			lastCommandWasUndo = false;
			
			history.add(command);
			effects.add(new HistoryAddCommandUpdate(command));
		}
		
		// Save the modeling commands
		if (!(instanceOf(command, "ResetKarmaCommand"))) {
			try {
				if(isHistoryWriteEnabled && historySavers.get(workspace.getId()) != null) {
					writeHistoryPerWorksheet(workspace, historySavers.get(workspace.getId()));
				}
			} catch (Exception e) {
				logger.error("Error occured while writing history!" , e);
				e.printStackTrace();
			}
		}
		return effects;
	}

	private void writeHistoryPerWorksheet(Workspace workspace, IHistorySaver historySaver) throws Exception {
		String workspaceId = workspace.getId();
		HashMap<String, JSONArray> comMap = new HashMap<String, JSONArray>();
		for(ICommand command : history) {
			if(command.isSavedInHistory() && (command.hasTag(CommandTag.Modeling) 
					|| command.hasTag(CommandTag.Transformation))) {
				JSONArray json = new JSONArray(command.getInputParameterJson());
				String worksheetId = HistoryJsonUtil.getStringValue(HistoryArguments.worksheetId.name(), json);
				//String worksheetName = workspace.getWorksheet(worksheetId).getTitle(); 
				if(comMap.get(worksheetId) == null)
					comMap.put(worksheetId, new JSONArray());
				comMap.get(worksheetId).put(getCommandJSON(workspace, command));
			}
		}
		
		for(String worksheetId : comMap.keySet()) {
			JSONArray comms = comMap.get(worksheetId);
			historySaver.saveHistory(workspaceId, worksheetId, comms);
		}
	}

	
	public JSONObject getCommandJSON(Workspace workspace, ICommand comm) {
		JSONObject commObj = new JSONObject();
		commObj.put(HistoryArguments.commandName.name(), comm.getCommandName());
		
		// Populate the tags
		JSONArray tagsArr = new JSONArray();
		for (CommandTag tag : comm.getTags())
			tagsArr.put(tag.name());
		commObj.put(HistoryArguments.tags.name(), tagsArr);
		
		JSONArray inputArr = new JSONArray(comm.getInputParameterJson());
		for (int i = 0; i < inputArr.length(); i++) {
			JSONObject inpP = inputArr.getJSONObject(i);
			
			/*** Check the input parameter type and accordingly make changes ***/
			if(HistoryJsonUtil.getParameterType(inpP) == ParameterType.hNodeId) {
				String hNodeId = inpP.getString(ClientJsonKeys.value.name());
				HNode node = workspace.getFactory().getHNode(hNodeId);
				JSONArray hNodeRepresentation = node.getJSONArrayRepresentation(workspace.getFactory());
				inpP.put(ClientJsonKeys.value.name(), hNodeRepresentation);
			
			} else if (HistoryJsonUtil.getParameterType(inpP) == ParameterType.worksheetId) {
				inpP.put(ClientJsonKeys.value.name(), "W");
			} else {
				// do nothing
			}
		}
		commObj.put(HistoryArguments.inputParameters.name(), inputArr);
		return commObj;
	}
	
	private boolean instanceOf(Object o, String className) { // TODO this is a hack, but instanceof doesn't really seem appropriate here
		return o.getClass().getName().toLowerCase().contains(className.toLowerCase());
	}

	/**
	 * @param workspace
	 * @param commandId
	 *            is the id of a command that should be either in the undo or
	 *            redo histories. If it is in none, then nothing will be done.
	 * @return the effects of the undone or redone commands.
	 * @throws CommandException
	 */
	public UpdateContainer undoOrRedoCommandsUntil(Workspace workspace,
			String commandId) throws CommandException {
		List<ICommand> commandsToUndo = getCommandsUntil(history, commandId);
		if (!commandsToUndo.isEmpty()) {
			lastCommandWasUndo = true;
			return undoCommands(workspace, commandsToUndo);
		} else {
			List<ICommand> commandsToRedo = getCommandsUntil(redoStack,
					commandId);
			if (!commandsToRedo.isEmpty()) {
				return redoCommands(workspace, commandsToRedo);
			} else {
				return new UpdateContainer();
			}
		}
	}

	/**
	 * Undo all commands in the given list.
	 * 
	 * @param workspace
	 * @param commandsToUndo
	 * 
	 * @return UpdateContainer with the effects of all undone commands.
	 */
	private UpdateContainer undoCommands(Workspace workspace,
			List<ICommand> commandsToUndo) {

		UpdateContainer effects = new UpdateContainer();
		for (ICommand c : commandsToUndo) {
			history.remove(c);
			redoStack.add(c);
			effects.append(c.undoIt(workspace));
		}
		return effects;
	}

	/**
	 * Redo all the commands in the given list.
	 * 
	 * @param workspace
	 * @param commandsToRedo
	 * @return
	 * @throws CommandException
	 */
	private UpdateContainer redoCommands(Workspace workspace,
			List<ICommand> commandsToRedo) throws CommandException {
		if (!redoStack.isEmpty()) {

			UpdateContainer effects = new UpdateContainer();
			for (ICommand c : commandsToRedo) {
				redoStack.remove(c);
				history.add(c);
				effects.append(c.doIt(workspace));
			}
			return effects;
		} else {
			return new UpdateContainer();
		}
	}

	/**
	 * @param commands
	 *            , a list of commands.
	 * @param commandId
	 *            , the id of a command.
	 * @return the sublist of commands from the start until and including a
	 *         command with the given id. If the command with the given id is
	 *         not in the list, return the empty list.
	 */
	private List<ICommand> getCommandsUntil(List<ICommand> commands,
			String commandId) {
		if (commands.isEmpty()) {
			return Collections.emptyList();
		}
		List<ICommand> result = new LinkedList<ICommand>();
		boolean foundCommand = false;
		for (int i = commands.size() - 1; i >= 0; i--) {
			ICommand c = commands.get(i);
			if (c.getCommandType() == CommandType.undoable) {
				result.add(c);
			}
			if (c.getId().equals(commandId)) {
				foundCommand = true;
				break;
			}
		}
		if (foundCommand) {
			return result;
		} else {
			return Collections.emptyList();
		}
	}

	public void generateFullHistoryJson(String prefix, PrintWriter pw,
			VWorkspace vWorkspace) {
		Iterator<ICommand> histIt = history.iterator();
		while (histIt.hasNext()) {
			histIt.next().generateJson(prefix, pw, vWorkspace,
					Command.HistoryType.undo);
			if (histIt.hasNext() || isRedoEnabled()) {
				pw.println(prefix + ",");
			}
		}

		for(int i = redoStack.size()-1; i>=0; i--) {
			ICommand redoComm = redoStack.get(i);
			redoComm.generateJson(prefix, pw, vWorkspace,
					Command.HistoryType.redo);
			if(i != 0)
				pw.println(prefix + ",");
		}
	}

	public void removeCommands(CommandTag tag) {
		List<ICommand> commandsToBeRemoved = new ArrayList<ICommand>();
		for(ICommand command: history) {
			if(command.hasTag(tag))
				commandsToBeRemoved.add(command);
		}
		history.removeAll(commandsToBeRemoved);
	}
	
	public ICommand getCommand(String commandId)
	{
		for(ICommand  c: this.history)
		{
			if(c.getId().equals(commandId))
			{
				return c;
			}
		}
		return null;
			
	}
	
	public List<ICommand> getCommands(CommandTag tag) {
		List<ICommand> retCommands = new ArrayList<ICommand>();
		for(ICommand command: history) {
			if(command.hasTag(tag))
				retCommands.add(command);
		}
		return retCommands;
	}
	
	
	private static boolean isHistoryWriteEnabled = false;
	public static boolean isHistoryEnabled()
	{
		return isHistoryWriteEnabled;
	}
	
	public static void setIsHistoryEnabled(boolean isHistoryWriteEnabled)
	{
		CommandHistory.isHistoryWriteEnabled = isHistoryWriteEnabled;
	}
	
	public static void setHistorySaver(String workspaceId, IHistorySaver saver) {
		historySavers.put(workspaceId, saver);
	}
	
	public static IHistorySaver getHistorySaver(String workspaceId) {
		return historySavers.get(workspaceId);
	}
}
