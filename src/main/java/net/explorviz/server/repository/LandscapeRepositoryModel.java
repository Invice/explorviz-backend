package net.explorviz.server.repository;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.nustaq.serialization.FSTConfiguration;

import explorviz.live_trace_processing.reader.IPeriodicTimeSignalReceiver;
import explorviz.live_trace_processing.reader.TimeSignalReader;
import explorviz.live_trace_processing.record.IRecord;
import net.explorviz.model.Application;
import net.explorviz.model.Clazz;
import net.explorviz.model.Communication;
import net.explorviz.model.CommunicationClazz;
import net.explorviz.model.Component;
import net.explorviz.model.Landscape;
import net.explorviz.model.Node;
import net.explorviz.model.NodeGroup;
import net.explorviz.model.System;
import net.explorviz.server.main.Configuration;

public final class LandscapeRepositoryModel implements IPeriodicTimeSignalReceiver {
	private static final boolean LOAD_LAST_LANDSCAPE_ON_LOAD = false;
	private static LandscapeRepositoryModel instance = null;

	private volatile Landscape lastPeriodLandscape;
	private final Landscape internalLandscape;
	private final FSTConfiguration fstConf;
	private final InsertionRepositoryPart insertionRepositoryPart;
	private final RemoteCallRepositoryPart remoteCallRepositoryPart;

	private LandscapeRepositoryModel() {
		fstConf = initFSTConf();

		if (LOAD_LAST_LANDSCAPE_ON_LOAD) {
			Landscape readLandscape = null;
			try {
				readLandscape = RepositoryStorage.readFromFile(java.lang.System.currentTimeMillis());
			} catch (final FileNotFoundException e) {
				readLandscape = new Landscape();
			}

			internalLandscape = readLandscape;
		} else {
			internalLandscape = new Landscape();
		}

		insertionRepositoryPart = new InsertionRepositoryPart();
		remoteCallRepositoryPart = new RemoteCallRepositoryPart();

		internalLandscape.updateLandscapeAccess(java.lang.System.nanoTime());

		final Landscape l = fstConf.deepCopy(internalLandscape);

		lastPeriodLandscape = LandscapePreparer.prepareLandscape(l);

		new TimeSignalReader(TimeUnit.SECONDS.toMillis(Configuration.outputIntervalSeconds), this).start();
	}

	public static synchronized LandscapeRepositoryModel getInstance() {
		if (LandscapeRepositoryModel.instance == null) {
			LandscapeRepositoryModel.instance = new LandscapeRepositoryModel();
		}
		return LandscapeRepositoryModel.instance;
	}

	public final Landscape getLastPeriodLandscape() {
		synchronized (lastPeriodLandscape) {
			return lastPeriodLandscape;
		}
	}

	public final Landscape getLandscape(final long timestamp) throws FileNotFoundException {
		return LandscapePreparer.prepareLandscape(RepositoryStorage.readFromFile(timestamp));
	}

	public final Map<Long, Long> getAvailableLandscapes() {
		return RepositoryStorage.getAvailableModelsForTimeshift();
	}

	static {
		Configuration.DATABASE_NAMES.add("hsqldb");
		Configuration.DATABASE_NAMES.add("postgres");
		Configuration.DATABASE_NAMES.add("db2");
		Configuration.DATABASE_NAMES.add("mysql");
		Configuration.DATABASE_NAMES.add("neo4j");
		Configuration.DATABASE_NAMES.add("database");
		Configuration.DATABASE_NAMES.add("hypersql");
	}

	public FSTConfiguration initFSTConf() {
		return RepositoryStorage.createFSTConfiguration();
	}

	public void reset() {
		synchronized (internalLandscape) {
			internalLandscape.getApplicationCommunication().clear();
			internalLandscape.getSystems().clear();
			internalLandscape.getEvents().clear();
			internalLandscape.getErrors().clear();
			internalLandscape.setActivities(0L);
			internalLandscape.updateLandscapeAccess(java.lang.System.nanoTime());
		}
	}

	@Override
	public void periodicTimeSignal(final long timestamp) {
		synchronized (internalLandscape) {
			synchronized (lastPeriodLandscape) {

				// TODO: passed timestamp meaning?
				// => using own created timestamp for creating landscape

				final long requestedTimestamp = java.lang.System.currentTimeMillis();

				if (!Configuration.DUMMYMODE) {
					RepositoryStorage.writeToFile(internalLandscape, requestedTimestamp);
					final Landscape l = fstConf.deepCopy(internalLandscape);
					l.setTimestamp(requestedTimestamp);
					lastPeriodLandscape = LandscapePreparer.prepareLandscape(l);
				} else {
					final Landscape dummyLandscape = LandscapeDummyCreator.createDummyLandscape();
					dummyLandscape.setTimestamp(requestedTimestamp);
					RepositoryStorage.writeToFile(dummyLandscape, requestedTimestamp);
					lastPeriodLandscape = dummyLandscape;
				}
				remoteCallRepositoryPart.checkForTimedoutRemoteCalls();
				resetCommunication();
			}
		}

		RepositoryStorage.cleanUpTooOldFiles(java.lang.System.currentTimeMillis());
	}

	private void resetCommunication() {
		internalLandscape.getErrors().clear();
		internalLandscape.setActivities(0L);

		for (final System system : internalLandscape.getSystems()) {
			for (final NodeGroup nodeGroup : system.getNodeGroups()) {
				for (final Node node : nodeGroup.getNodes()) {
					for (final Application app : node.getApplications()) {
						app.getDatabaseQueries().clear();

						for (final CommunicationClazz commu : app.getCommunications()) {
							commu.reset();
						}

						resetClazzInstances(app.getComponents());
					}
				}
			}
		}

		for (final Communication commu : internalLandscape.getApplicationCommunication()) {
			commu.setRequests(0);
			commu.setAverageResponseTimeInNanoSec(0);
		}

		internalLandscape.updateLandscapeAccess(java.lang.System.nanoTime());
	}

	private void resetClazzInstances(final List<Component> components) {
		for (final Component compo : components) {
			for (final Clazz clazz : compo.getClazzes()) {
				clazz.getObjectIds().clear();
				clazz.setInstanceCount(0);
			}

			resetClazzInstances(compo.getChildren());
		}
	}

	public void insertIntoModel(final IRecord inputIRecord) {
		insertionRepositoryPart.insertIntoModel(inputIRecord, internalLandscape, remoteCallRepositoryPart);
	}
}
