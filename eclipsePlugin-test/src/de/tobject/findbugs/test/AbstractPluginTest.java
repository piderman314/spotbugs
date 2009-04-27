/*
 * Contributions to FindBugs
 * Copyright (C) 2009, Tom�s Pollak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package de.tobject.findbugs.test;

import static org.eclipse.core.runtime.jobs.Job.*;
import static org.eclipse.jdt.testplugin.JavaProjectHelper.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.junit.buildpath.BuildPathSupport;
import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.junit.After;
import org.junit.Before;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.FindbugsTestPlugin;
import de.tobject.findbugs.builder.FindBugsWorker;
import de.tobject.findbugs.builder.WorkItem;
import de.tobject.findbugs.marker.FindBugsMarker;
import de.tobject.findbugs.reporter.MarkerUtil;
import de.tobject.findbugs.view.BugExplorerView;
import de.tobject.findbugs.view.explorer.BugContentProvider;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.SortedBugCollection;
import edu.umd.cs.findbugs.config.UserPreferences;

/**
 * Base class for FindBugs tests.
 * 
 * @author Tom�s Pollak
 */
public abstract class AbstractPluginTest {

	protected static final String BUG_EXPLORER_VIEW_ID = "de.tobject.findbugs.view.bugtreeview";
	protected static final String SRC = "src";
	protected static final String TEST_PROJECT = "TestProject";
	private IJavaProject project;

	public AbstractPluginTest() {
		super();
	}

	/**
	 * Create a new Java project with a source folder and copy the test files of the
	 * plugin to the source folder. Compile the project.
	 * 
	 * @throws CoreException
	 * @throws IOException
	 */
	@Before
	public void setUp() throws CoreException, IOException {
		// Create the test project
		project = createJavaProject(TEST_PROJECT, "bin");
		addRTJar(getJavaProject());
		addSourceContainer(getJavaProject(), SRC);
		addExtraClassPathEntries();

		// Copy test workspace
		importResources(getProject().getFolder(SRC), FindbugsTestPlugin.getDefault()
				.getBundle(), getTestFilesPath());

		// Compile project
		getProject().getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);
		performDummySearch();

		// Start with a clean FindBugs state
		clearBugsState();
	}

	/**
	 * Delete the Java project used for this test.
	 * 
	 * @throws CoreException
	 */
	@After
	public void tearDown() throws CoreException {
		// Clean the FindBugs state
		clearBugsState();

		// Delete the test project
		delete(project);
	}

	/**
	 * Hook for subclasses to add extra classpath entries to the test project during the
	 * setup of the test.
	 */
	protected void addExtraClassPathEntries() throws CoreException, IOException {
		// Add JUnit if the test scenario requires it
		if (getTestScenario().usesJUnit()) {
			addJUnitToProjectClasspath();
		}
	}

	protected final void addJUnitToProjectClasspath() throws JavaModelException {
		IClasspathEntry cpe = BuildPathSupport.getJUnit3ClasspathEntry();
		JavaProjectHelper.addToClasspath(getJavaProject(), cpe);
	}

	/**
	 * Assert the total number of bugs in the given resource.
	 * 
	 * @param expected
	 *            The expected number of bugs.
	 * @param project
	 *            The IProject that contains the bugs.
	 * @throws CoreException
	 */
	protected void assertBugsCount(int expected, IProject project) throws CoreException {
		SortedBugCollection bugs = FindbugsPlugin.getBugCollection(project, null);
		assertEquals(expected, bugs.getCollection().size());
	}

	protected void assertExpectedBugs() throws CoreException {
		assertBugsCount(getExpectedBugsCount(), getProject());
		for (String bugPattern : getTestScenario().getVisibleBugs()) {
			int frequency = getTestScenario().getVisibleBugFrequency(bugPattern);
			assertReportedBugs(bugPattern, frequency, getProject());
		}
	}

	protected void assertExpectedMarkers(IMarker[] markers) throws CoreException {
		assertEquals(getVisibleBugsCount(), markers.length);
		for (int i = 0; i < markers.length; i++) {
			assertTrue(markers[i].isSubtypeOf(FindBugsMarker.NAME));
		}
		for (String bugPattern : getTestScenario().getVisibleBugs()) {
			int frequency = getTestScenario().getVisibleBugFrequency(bugPattern);
			assertMarkers(bugPattern, frequency, markers);
		}
	}

	protected void assertExpectedMarkers(Set<IMarker> markers) throws CoreException {
		assertExpectedMarkers(markers.toArray(new IMarker[0]));
	}

	/**
	 * Asserts that the number of present markers of the given type match the given
	 * expected count.
	 * 
	 * @param expectedBugType
	 *            The expected bug type.
	 * @param expectedBugCount
	 *            The expected bug type count.
	 * @param markers
	 *            The array of markers to assert on.
	 * @throws CoreException
	 */
	protected void assertMarkers(String expectedBugType, int expectedBugTypeCount,
			IMarker[] markers) throws CoreException {
		int seenBugTypeCount = 0;
		for (int i = 0; i < markers.length; i++) {
			IMarker marker = markers[i];
			if (expectedBugType.equals(marker.getAttribute(FindBugsMarker.BUG_TYPE))) {
				seenBugTypeCount++;
			}
		}
		assertEquals("Expected " + expectedBugTypeCount + " of markers "
				+ expectedBugType + " but seen " + seenBugTypeCount,
				expectedBugTypeCount, seenBugTypeCount);
	}

	protected void assertNoBugs() throws CoreException {
		assertBugsCount(0, getProject());
	}

	protected void assertNoMarkers(IMarker[] markers) {
		assertEquals(0, markers.length);
	}

	/**
	 * Asserts that the number of detected bugs of the given type match the given expected
	 * count.
	 * 
	 * @param expectedBugType
	 *            The expected bug type.
	 * @param expectedBugCount
	 *            The expected bug type count.
	 * @param project
	 *            The IProject that contains the bugs.
	 * @throws CoreException
	 */
	protected void assertReportedBugs(String expectedBugType, int expectedBugCount,
			IProject project) throws CoreException {
		int seenBugCount = 0;
		SortedBugCollection bugs = FindbugsPlugin.getBugCollection(project, null);
		for (BugInstance bug : bugs) {
			if (expectedBugType.equals(bug.getType())) {
				seenBugCount++;
			}
		}
		assertEquals("Expected " + expectedBugCount + " of bugs " + expectedBugType
				+ " but seen " + seenBugCount, expectedBugCount, seenBugCount);
	}

	protected void clearBugsState() throws CoreException {
		MarkerUtil.removeMarkers(getProject());
		FindbugsPlugin.getBugCollection(getProject(), null).clearBugInstances();
	}

	protected FindBugsWorker createFindBugsWorker() throws CoreException {
		FindBugsWorker worker = new FindBugsWorker(getProject(),
				new NullProgressMonitor());
		return worker;
	}

	protected BugContentProvider getBugContentProvider() throws PartInitException {
		BugExplorerView navigator = (BugExplorerView) showBugExplorerView();
		BugContentProvider bugContentProvider = BugContentProvider.getProvider(navigator
				.getNavigatorContentService());
		return bugContentProvider;
	}

	protected Set<String> getExpectedBugPatterns() {
		return getTestScenario().getVisibleBugs();
	}

	protected final int getExpectedBugsCount() {
		return getFilteredBugsCount() + getVisibleBugsCount();
	}

	protected final int getFilteredBugsCount() {
		return getTestScenario().getFilteredBugsCount();
	}

	/**
	 * Returns the Java project for this test.
	 * 
	 * @return An IJavaProject.
	 */
	protected IJavaProject getJavaProject() {
		return project;
	}

	/**
	 * Returns the project for this test.
	 * 
	 * @return An IProject.
	 */
	protected IProject getProject() {
		return getJavaProject().getProject();
	}

	protected UserPreferences getProjectPreferences() {
		return FindbugsPlugin.getProjectPreferences(getProject(), false);
	}

	/**
	 * Subclasses must implement this method and return a path inside of the bundle where
	 * the test files are located.
	 */
	protected final String getTestFilesPath() {
		return getTestScenario().getTestFilesPath();
	}

	/**
	 * Returns the TestScenario for this test.
	 */
	protected abstract TestScenario getTestScenario();

	protected final int getVisibleBugsCount() {
		return getTestScenario().getVisibleBugsCount();
	}

	/**
	 * Suspend the calling thread until all the background jobs belonging to the given
	 * family are done.
	 * 
	 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(Object)
	 * 
	 * @param family
	 *            The family object that groups the jobs.
	 */
	protected void joinJobFamily(Object family) {
		boolean finished = false;
		while (!finished) {
			try {
				getJobManager().join(family, null);
				finished = true;
			} catch (InterruptedException e) {
				// continue waiting
			}
		}
	}

	protected void loadXml(FindBugsWorker worker, String bugsFileLocation)
			throws CoreException {
		worker.loadXml(bugsFileLocation);
	}

	protected IViewPart showBugExplorerView() throws PartInitException {
		return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				.showView(BUG_EXPLORER_VIEW_ID);
	}

	/**
	 * Runs the FindBugs worker on the test project.
	 */
	protected void work(FindBugsWorker worker) throws CoreException {
		work(worker, getJavaProject());
	}

	/**
	 * Runs the FindBugs worker on the given Java element.
	 */
	protected void work(FindBugsWorker worker, IJavaElement element) throws CoreException {
		worker.work(Collections.singletonList(new WorkItem(element)));
	}

	/**
	 * Runs the FindBugs worker on the given resource.
	 */
	protected void work(FindBugsWorker worker, IResource resource) throws CoreException {
		worker.work(Collections.singletonList(new WorkItem(resource)));
	}
}