package ai.kiya.process.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ai.kiya.process.entities.ProcessGrouping;
import ai.kiya.process.repositories.ProcessGroupingRepo;
import ai.kiya.process.service.ProcessGroupingService;

@Service
public class ProcessGroupingServiceImpl implements ProcessGroupingService {

	@Autowired
	private ProcessGroupingRepo groupingRepo;
	
	@Override
	public List<ProcessGrouping> getAllProcessGroups() {
		List<ProcessGrouping> groups = groupingRepo.findAllProcesses();
		return groups;
	}

}
