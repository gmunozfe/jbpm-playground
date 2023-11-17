package com.company.service;

import org.drools.core.process.instance.WorkItemHandler;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;

import java.util.HashMap;
import java.util.Map;

public class HelloItemHandler implements WorkItemHandler {
	@Override
	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
	    manager.abortWorkItem(workItem.getId());
	}

	@Override
	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {

	    Object name = workItem.getParameter("name");
	    System.out.println("Hi " + name);
	    Map<String, Object> results = new HashMap<String, Object>();
	    results.put("helloVar", name+" was here");

	    manager.completeWorkItem(workItem.getId(), results);
	}
}