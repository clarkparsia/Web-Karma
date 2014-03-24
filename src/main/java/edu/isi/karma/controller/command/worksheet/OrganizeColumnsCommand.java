package edu.isi.karma.controller.command.worksheet;

import  org.json.JSONArray;

import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.WorksheetCommand;

import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.controller.update.WorksheetUpdateFactory;
import edu.isi.karma.rep.Workspace;

public class OrganizeColumnsCommand extends WorksheetCommand {

	private JSONArray prevOrderedColumns;
	private JSONArray orderedColumns;
	
	protected OrganizeColumnsCommand(String id, String worksheetId, org.json.JSONArray orderedColumns) {
		super(id, worksheetId);
		this.orderedColumns = orderedColumns;
	}

	@Override
	public String getCommandName() {
		return OrganizeColumnsCommand.class.getSimpleName();
	}

	@Override
	public String getTitle() {
		return "Organize Columns";
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CommandType getCommandType() {
		return CommandType.undoable;
	}

	@Override
	public UpdateContainer doIt(Workspace workspace) throws CommandException {
		//Calculate the prevOrderColumns
		
		orderColumns(orderedColumns);
		
		UpdateContainer c =  new UpdateContainer();
		c.append(WorksheetUpdateFactory.createRegenerateWorksheetUpdates(worksheetId));
		c.append(computeAlignmentAndSemanticTypesAndCreateUpdates(workspace));
		return c;
	}

	@Override
	public UpdateContainer undoIt(Workspace workspace) {
		if(prevOrderedColumns != null) {
			orderColumns(prevOrderedColumns);
		}
		UpdateContainer c =  new UpdateContainer();
		c.append(WorksheetUpdateFactory.createRegenerateWorksheetUpdates(worksheetId));
		c.append(computeAlignmentAndSemanticTypesAndCreateUpdates(workspace));
		return c;
	}

	private void orderColumns(JSONArray columns) {
		
	}
}
