/******************************************************************************
 * Copyright (c) 2009-2016 Telink Semiconductor Co., LTD.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * -----------------------------------------------------------------------------
 * Module:
 * Purpose:
 * Reference :   
 * $Id: DirectoryNotStrictVariableFieldEditor.java 851 20.1.08-07 19:37:00Z innot $
 *******************************************************************************//******************************************************************************
 * Copyright (c) 2009-2016 Telink Semiconductor Co., LTD.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * -----------------------------------------------------------------------------
 * Module:
 * Purpose:
 * Reference : 
 * $Id: PCDB.java 851 20.1.08-07 19:37:00Z innot $
 *     
 *******************************************************************************/
package com.telink.tc32eclipse.core.toolinfo;

import java.io.BufferedReader;
import java.io.File;
//import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
//import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
//import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
//import org.eclipse.ui.console.IOConsoleOutputStream;
import org.eclipse.ui.console.MessageConsole;

import com.telink.tc32eclipse.TC32Plugin;
import com.telink.tc32eclipse.core.IMCUProvider;
import com.telink.tc32eclipse.core.paths.TC32Path;
import com.telink.tc32eclipse.core.paths.TC32PathProvider;
import com.telink.tc32eclipse.core.paths.IPathProvider;
import com.telink.tc32eclipse.core.preferences.TCDBPreferences;
//import com.telink.tc32eclipse.core.targets.ClockValuesGenerator;
import com.telink.tc32eclipse.core.targets.HostInterface;
import com.telink.tc32eclipse.core.targets.IProgrammer;
import com.telink.tc32eclipse.core.targets.TargetInterface;
//import com.telink.tc32eclipse.core.targets.ClockValuesGenerator.ClockValuesType;
//import com.telink.tc32eclipse.core.tcdb.TCDBAction;
//import com.telink.tc32eclipse.core.tcdb.TCDBActionFactory;
import com.telink.tc32eclipse.core.tcdb.TCDBException;
import com.telink.tc32eclipse.core.tcdb.ProgrammerConfig;
import com.telink.tc32eclipse.core.tcdb.TCDBException.Reason;


/**
 * This class handles all interactions with the TCDB program.
 * <p>
 * It implements the {@link IMCUProvider} Interface to get a list of all MCUs supported by the
 * selected version of TCDB. Additional methods are available to get a list of all supported
 * Programmers.
 * </p>
 * <p>
 * This class implements the Singleton pattern. Use the {@link #getDefault()} method to get the
 * instance of this class.
 * </p>
 * 
 * @author Peter Shieh
 * @since 0.1
 */
public class TCDB implements IMCUProvider {

	/** The singleton instance of this class */
	private static TCDB	instance	= null;

	/** The preference store for TCDB */
	private final IPreferenceStore		fPrefsStore;


	/**
	 * A list of all currently supported Programmer devices, mapped to their ID.
	 */
	private Map<String, IProgrammer>		fProgrammerList;

	/**
	 * A List of all Programmer ConfigEntries to their respective id's.
	 */
	private Map<String, ConfigEntry>		fProgrammerConfigEntries;

	/**
	 * Mapping of the Plugin MCU Id values (as keys) to the TCDB mcu id values (as values)
	 */
	//private Map<String, String>				fMCUIdMap			= null;

	/** The current path to the directory of the TCDB executable */
	private IPath							fCurrentPath		= null;

	/** The name of the TCDB executable */
	private final static String				fCommandName		= "tcdb.exe";

	/** The Path provider for the TCDB executable */
	private final IPathProvider					fPathProvider		= new TC32PathProvider(
																			TC32Path.TC32_TOOLS);

	/** Bug 3023718: Remember the last MCU connected to a given programmer */
	//private final Map<ProgrammerConfig, String>	fLastMCUtypeMap		= new HashMap<ProgrammerConfig, String>();

//	private long							fLastTCDBFinish	= 0L;

	/**
	 * A cache of one or more TCDB config files. The config files are stored as
	 * List&lt;String&gt; with one entry per line
	 */
	private final Map<IPath, List<String>>	fConfigFileCache	= new HashMap<IPath, List<String>>();
	
	private final static String             osName = System.getProperty("os.name").toLowerCase();

	/**
	 * Get the singleton instance of the TCDB class.
	 */
	public static TCDB getDefault() {
		if (instance == null)
			instance = new TCDB();
		return instance;
	}

	// Prevent Instantiation of the class
	private TCDB() {
		fPrefsStore = TCDBPreferences.getPreferenceStore();
	}

	/**
	 * Returns the name of the TCDB executable.
	 * <p>
	 * On Windows Systems the ".exe" extension is not included and needs to be added for access to
	 * TCDB other than executing the programm.
	 * </p>
	 * 
	 * @return String with "TCDB"
	 */
	public String getCommandName() {
		if(osName.indexOf("win")<0) 
			return "tcdb";
		return fCommandName;
	}

	/**
	 * Returns the full path to the TCDB executable.
	 * <p>
	 * Note: On Windows Systems the returned path does not include the ".exe" extension.
	 * </p>
	 * 
	 * @return <code>IPath</code> to the TCDB executable
	 */
	public IPath getToolFullPath() {
		IPath path = fPathProvider.getPath();
		return path.append(getCommandName());
	}

	
	


	/**
	 * Returns the {@link IProgrammer} for a given programmer id.
	 * 
	 * @param programmerid
	 *            A valid programmer id as used by TCDB.
	 * @return the programmer type object or <code>null</code> if the <code>programmerid</code> is
	 *         unknown.
	 * @throws TCDBException
	 */
	public IProgrammer getProgrammer(String programmerid) throws TCDBException {
		loadProgrammersList(); // update the internal list (if required)
		IProgrammer type = fProgrammerList.get(programmerid);
		return type;
	}

	/**
	 * Returns a List of all currently supported Programmer devices.
	 * 
	 * @return <code>Set&lt;String&gt</code> with the TCDB id values.
	 * @throws TCDBException
	 */
	public List<IProgrammer> getProgrammersList() throws TCDBException {
		Collection<IProgrammer> list = loadProgrammersList().values();

		// Return a copy of the original list
		return new ArrayList<IProgrammer>(list);
	}

	/**
	 * Returns a Set of all currently supported Programmer devices.
	 * 
	 * @return <code>Set&lt;String&gt</code> with the TCDB id values.
	 * @throws TCDBException
	 */
	public Set<String> getProgrammerIDs() throws TCDBException {
		Set<String> allids = loadProgrammersList().keySet();

		// Return a copy of the original list
		return new HashSet<String>(allids);
	}

	/**
	 * Returns the {@link ConfigEntry} for the given Programmer device.
	 * 
	 * @param programmerid
	 *            <code>String</code> with the TCDB id of the programmer
	 * @return <code>ConfigEntry</code> containing all known information extracted from the TCDB
	 *         executable
	 * @throws TCDBException
	 */
	public ConfigEntry getProgrammerInfo(String programmerid) throws TCDBException {
		loadProgrammersList(); // update the list (if required)

		return fProgrammerConfigEntries.get(programmerid);
	}

	/**
	 * Returns the section of the TCDB.conf configuration file describing the the given
	 * ConfigEntry.
	 * <p>
	 * The extract is returned as a multiline <code>String</code> that can be used directly in an
	 * Text Control in the GUI.
	 * </p>
	 * <p>
	 * Note: The first call to this method may take some time, as the complete TCDB.conf file is
	 * read and and split into lines (currently around 450 Kbyte). This method is Synchronized, so
	 * it is safe to call it multiple times.
	 * 
	 * @param entry
	 *            The <code>ConfigEntry</code> for which to get the TCDB.conf entry.
	 * @return A <code>String</code> with the relevant lines, separated with '\n'.
	 * @throws IOException
	 *             Any Exception reading the configuration file.
	 */
	public synchronized String getConfigDetailInfo(ConfigEntry entry) throws IOException {

		List<String> configcontent = null;
		// Test if we have already loaded the config file
		IPath configpath = entry.configfile;
		if (fConfigFileCache.containsKey(configpath)) {
			configcontent = fConfigFileCache.get(configpath);
		} else {
			// Load the config file
			configcontent = loadConfigFile(configpath);
			fConfigFileCache.put(configpath, configcontent);
		}

		// make a string, starting from the given line until the first line that
		// does not start with a whitespace
		StringBuffer result = new StringBuffer();

		// copy every line from the config file until we hit a single ';' in the first column

		int index = entry.linenumber;
		while (true) {
			String line = configcontent.get(index++);
			if (line.startsWith(";")) {
				break;
			}
			result.append(line.trim()).append('\n');
		}
		return result.toString();
	}

	/**
	 * Return the MCU id value of the device currently attached to the given Programmer.
	 * 
	 * @param config
	 *            <code>ProgrammerConfig</code> with the Programmer to query.
	 * @return <code>String</code> with the id of the attached MCU.
	 * @throws TCDBException
	 */
	/*
	public String getAttachedMCU(ProgrammerConfig config, IProgressMonitor monitor)
			throws TCDBException {

		try {
			monitor.beginTask("Getting attached MCU", 100);
			if (config == null)
				throw new TCDBException(Reason.NO_PROGRAMMER, "", null);

			// Bug 3023718: List of TCDB mcu id's to test. The first entry will be replaced
			// by the last muc used with this programmer.
			String[] testmcus = { "tc32"};
			String lastMCU = fLastMCUtypeMap.get(config);
			if (lastMCU != null) {
				testmcus[0] = fMCUIdMap.get(lastMCU);
			}

			for (String testmcu : testmcus) {

				if ("".equals(testmcu)) {
					continue;
				}

				List<String> configoptions = config.getArguments();
				configoptions.add("-p" + testmcu);

				try {
					List<String> stdout = runCommand(configoptions, new SubProgressMonitor(monitor,
							30), false, null, config);
					if (stdout == null) {
						continue;
					}

				} catch (TCDBException ade) {
					if (ade.getReason().equals(Reason.INIT_FAIL)) {
						// if the init failed then (maybe) we tried the wrong MCU.
						// continue with the next one:
						continue;
					} else {
						throw ade;
					}
				}

				// did not find a signature. Try the next candidate.

			}
			// Signature not found. This probably means that our simple parser is
			// broken
			throw new TCDBException(Reason.PARSE_ERROR,
					"Could not find a valid Signature in the TCDB output", null);
		} finally {
			monitor.done();
		}
	}

*/
	

	/**
	 * Return the current erase cycle counter of the device currently attached to the given
	 * Programmer.
	 * <p>
	 * The value is read by calling avdude with the "-y" option.
	 * </p>
	 * 
	 * @param config
	 *            <code>ProgrammerConfig</code> with the Programmer to query.
	 * @return <code>int</code> with the values or <code>-1</code> if the counter value is not set.
	 * @throws TCDBException
	 */

	public int getEraseCycleCounter(ProgrammerConfig config, IProgressMonitor monitor)
			throws TCDBException {

		try {
			monitor.beginTask("Read Erase Cycle Counter", 100);
			// First get the attached MCU
			String mcuid = "tc32"; //getAttachedMCU(config, new SubProgressMonitor(monitor, 20));

			List<String> args = new ArrayList<String>(config.getArguments());
			args.add("-p" + getMCUInfo(mcuid));

			List<String> stdout = runCommand(args, new SubProgressMonitor(monitor, 80), false,
					null, config);
			if (stdout == null) {
				return -1;
			}

			// Parse the output and look for a line "TCDB: current erase-rewrite cycle count is
			// xx"
			Pattern mcuPat = Pattern.compile(".*erase-rewrite cycle count.*?([0-9]+).*");
			Matcher m;

			for (String line : stdout) {
				m = mcuPat.matcher(line);
				if (!m.matches()) {
					continue;
				}
				// pattern matched. Get the cycle count and return it as an int
				return Integer.parseInt(m.group(1));
			}
			// Cycle count not found. This probably means that no cycle count has been set yet
			return -1;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Set the erase cycle counter of the device currently attached to the given Programmer.
	 * <p>
	 * The value is set by calling avdude with the "-Y xxxx" option. The method returns the new
	 * value as read from the MCU as a crosscheck that the value has been written.
	 * </p>
	 * 
	 * @param config
	 *            <code>ProgrammerConfig</code> with the Programmer to query.
	 * @return <code>int</code> with the values or <code>-1</code> if the counter value is not set.
	 * @throws TCDBException
	 */
	
public int setEraseCycleCounter(ProgrammerConfig config, int newcounter,
			IProgressMonitor monitor) throws TCDBException {

		try {
			monitor.beginTask("Setting Erase Cylce Counter", 100);
			// First get the attached MCU
			String mcuid = "tc32"; //getAttachedMCU(config, new SubProgressMonitor(monitor, 20));

			List<String> args = new ArrayList<String>(config.getArguments());
			args.add("-p" + getMCUInfo(mcuid));
			args.add("-Y" + (newcounter & 0xffff));

			runCommand(args, new SubProgressMonitor(monitor, 60), false, null, config);

			// return the current value of the device as a crosscheck.
			return getEraseCycleCounter(config, new SubProgressMonitor(monitor, 20));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Tries to guess the target interface from the TCDB config id and the driver type from
	 * TCDB.conf.
	 * <p>
	 * This has been tested with TCDB 5.6. Future versions of TCDB might return wrong results.
	 * </p>
	 * 
	 * @param TCDBid
	 *            The id of the programmer as used by TCDB.
	 * @return The interface used by the programmer.
	 */
	public static TargetInterface getInterface(String TCDBid, String type) {

		// Check the serial bootloader types
		//if (type.equals("avr910") || type.equals("butterfly")) {
		//	return TargetInterface.BOOTLOADER;
		//}

		// Check if this is one of the HVSP supporting devices
		//if (TCDBid.endsWith("hvsp")) {
		//	return TargetInterface.HVSP;
		//}

		// Check if this is one of the PP supporting devices
		//if (TCDBid.endsWith("pp")) {
		//	return TargetInterface.PP;
		//}

		// Check if this is one of the DebugWire supporting devices
		//if (TCDBid.endsWith("dw")) {
		//	return TargetInterface.DW;
		//}

		// First check for ISP devices (to filter JTAG devices in ISP mode)
		//if (TCDBid.endsWith("isp")) {
		//	return TargetInterface.ISP;
		//}

		// Check if this is JTAG device
		if (TCDBid.contains("jtag")) {
			return TargetInterface.JTAG;
		}

		// I think we filtered everything out, so what is left is a normal ISP programmer.
		//return TargetInterface.ISP;
		return TargetInterface.USB;

	}

	//final static HostInterface[]	SERIALBBPORT	= new HostInterface[] { HostInterface.SERIAL_BB };
	//final static HostInterface[]	SERIALPORT		= new HostInterface[] { HostInterface.SERIAL };
	//final static HostInterface[]	PARALLELPORT	= new HostInterface[] { HostInterface.PARALLEL };
	final static HostInterface[]	USBPORT			= new HostInterface[] { HostInterface.USB };
	//final static HostInterface[]	SERIAL_USB_PORT	= new HostInterface[] { HostInterface.SERIAL,
	//		HostInterface.USB						};

	/**
	 * Tries to guess the host interface from the TCDB programmer id and type.
	 * <p>
	 * This has been tested with TCDB 5.6. Future versions of TCDB might return wrong results.
	 * </p>
	 * 
	 * @param TCDBid
	 *            The id of the programmer as used by TCDB.
	 * @return The interface used by the programmer.
	 */
	public static HostInterface[] getHostInterfaces(String TCDBid, String TCDBtype) {

		String type = TCDBtype.toLowerCase(); // just in case
		if (type.contains("usb")) {
			return USBPORT;
		}
		if (type.startsWith("tc32")) {
			return USBPORT;
		}

		// TODO remove when testing is finished
		throw new IllegalArgumentException("Can't determine host interface");
	}

	/**
	 * Internal method to read the config file with the given path and split it into lines.
	 * 
	 * @param path
	 *            <code>IPath</code> to a configuration file.
	 * @return A <code>List&lt;String&gt;</code> with all lines of the given configuration file
	 * @throws IOException
	 *             Any Exception reading the configuration file.
	 */
	private List<String> loadConfigFile(IPath path) throws IOException {

		// The default TCDB.conf file has some 12.000+ lines, however custom
		// TCDB.conf files might be much smaller, so we start with 100 lines
		// and let the ArrayList grow as required
		List<String> content = new ArrayList<String>(100);

		BufferedReader br = null;

		try {
			File configfile = path.toFile();
			br = new BufferedReader(new FileReader(configfile));

			String line;
			while ((line = br.readLine()) != null) {
				content.add(line);
			}

		} finally {
			if (br != null)
				br.close();
		}
		return content;
	}

	
	/**
	 * @return Map&lt;mcu id, TCDB id&gt; of all supported Programmer devices.
	 * @throws TCDBException
	 */
	private Map<String, IProgrammer> loadProgrammersList() throws TCDBException {

		if (!getToolFullPath().equals(fCurrentPath)) {
			// toolpath has changed, reload the list
			fProgrammerList = null;
			fCurrentPath = getToolFullPath();
		}

		if (fProgrammerList == null) {
			fProgrammerConfigEntries = new HashMap<String, ConfigEntry>();
			// Execute TCDB with the "-c?" to get a list of all supported
			// programmers.
			readTCDBConfigOutput(fProgrammerConfigEntries, "-c?");

			// Convert the ConfigEntries to IProgrammerTypes
			fProgrammerList = new HashMap<String, IProgrammer>();
			for (String id : fProgrammerConfigEntries.keySet()) {
				IProgrammer type = new ProgrammerType(id);
				fProgrammerList.put(id, type);
			}
		}
		return fProgrammerList;
	}

	/**
	 * Internal method to execute TCDB and parse the output as ConfigEntries.
	 * 
	 * @see #loadMCUList()
	 * @see #loadProgrammersList()
	 * 
	 * @param resultmap
	 * @param arguments
	 * @throws TCDBException
	 */
	private void readTCDBConfigOutput(Map<String, ConfigEntry> resultmap, String... arguments)
			throws TCDBException {

		List<String> stdout = runCommand(arguments);
		if (stdout == null) {
			return;
		}

		// TCDB output for configuration items looks like:
		// " id = description [pathtoTCDB.conf:line]"
		// The following pattern splits this into the four groups:
		// id / description / path / line
		Pattern mcuPat = Pattern.compile("\\s*(\\S+)\\s*=\\s*(.+?)\\s*\\[(.+):(\\d+)\\]\\.*");
		Matcher m;

		for (String line : stdout) {
			m = mcuPat.matcher(line);
			if (!m.matches()) {
				continue;
			}
			ConfigEntry entry = new ConfigEntry();
			entry.TCDBid = m.group(1);
			entry.description = m.group(2);
			entry.configfile = new Path(m.group(3));
			entry.linenumber = Integer.valueOf(m.group(4));

			resultmap.put(entry.TCDBid, entry);
		}
	}

	/**
	 * Get the command name and the current version of TCDB.
	 * <p>
	 * The name is defined in {@link #fCommandName}. The version is gathered by executing with the
	 * "-v" option and parsing the output.
	 * </p>
	 * 
	 * @return <code>String</code> with the command name and version
	 * @throws TCDBException
	 */
	public String getNameAndVersion() throws TCDBException {

		// Execute TCDB with the "-v" option and parse the
		// output.
		// Just "-v" will have TCDB complain about a missing programmer => TCDBException.
		// So we supply a dummy (but existing) programmer. TCDB will now complain about the
		// missing part, but we can ignore this because we got what we wanted: the version number.
		List<String> stdout = runCommand("-v", "");
		if (stdout == null) {
			// Return default name on failures
			return getCommandName() + " n/a";
		}

		// look for a line matching "*Version TheVersionNumber *"
		Pattern mcuPat = Pattern.compile(".*Version\\s+([\\d\\.]+).*");
		Matcher m;
		for (String line : stdout) {
			m = mcuPat.matcher(line);
			if (!m.matches()) {
				continue;
			}
			return getCommandName() + " " + m.group(1);
		}

		// could not read the version from the output, probably the regex has a
		// mistake. Return a reasonable default.
		return getCommandName() + " ?.?";
	}

	/**
	 * Runs TCDB with the given arguments.
	 * <p>
	 * The Output of stdout and stderr are merged and returned in a <code>List&lt;String&gt;</code>.
	 * </p>
	 * <p>
	 * If the command fails to execute an entry is written to the log and an
	 * {@link TCDBException} with the reason is thrown.
	 * </p>
	 * 
	 * @param arguments
	 *            Zero or more arguments for TCDB
	 * @return A list of all output lines, or <code>null</code> if the command could not be
	 *         launched.
	 * @throws TCDBException
	 *             when TCDB cannot be started or when TCDB returned an
	 */
	public List<String> runCommand(String... arguments) throws TCDBException {

		List<String> arglist = new ArrayList<String>(1);
		for (String arg : arguments) {
			arglist.add(arg);
		}

		return runCommand(arglist, new NullProgressMonitor(), false, null, null);
	}

	/**
	 * Runs TCDB with the given arguments.
	 * <p>
	 * The Output of stdout and stderr are merged and returned in a <code>List&lt;String&gt;</code>.
	 * If the "use Console" flag is set in the Preferences, the complete output is shown on a
	 * Console as well.
	 * </p>
	 * <p>
	 * If the command fails to execute an entry is written to the log and an
	 * {@link TCDBException} with the reason is thrown.
	 * </p>
	 * 
	 * @param arguments
	 *            <code>List&lt;String&gt;</code> with the arguments
	 * @param monitor
	 *            <code>IProgressMonitor</code> to cancel the running process.
	 * @param forceconsole
	 *            If <code>true</code> all output is copied to the console, regardless of the "use
	 *            console" flag.
	 * @param cwd
	 *            <code>IPath</code> with a current working directory or <code>null</code> to use
	 *            the default working directory (usually the one defined with the system property
	 *            <code>user.dir</code). May not be empty.
	 * @param programmerconfig
	 *            The TCDB Programmer configuration currently is use. Required for the TCDB
	 *            invocation delay value. If <code>null</code> no invocation delay will be done.
	 * @return A list of all output lines, or <code>null</code> if the command could not be
	 *         launched.
	 * @throws TCDBException
	 *             when TCDB cannot be started or when TCDB returned an error errors.
	 */
	public List<String> runCommand(List<String> arglist, IProgressMonitor monitor,
			boolean forceconsole, IPath cwd, ProgrammerConfig programmerconfig)
			throws TCDBException {

		try {
			monitor.beginTask("Running Telink Tools", 100);

			// Check if the CWD is valid
			if (cwd != null && cwd.isEmpty()) {
				throw new TCDBException(Reason.INVALID_CWD,
						"CWD does not point to a valid directory.");
			}

			// PS String command = getToolFullPath().toOSString();
			String command = getToolFullPath().toPortableString();

			// Check if the user has a custom configuration file
			IPreferenceStore TCDBprefs = TCDBPreferences.getPreferenceStore();
			boolean usecustomconfig = TCDBprefs
					.getBoolean(TCDBPreferences.KEY_USECUSTOMCONFIG);
			
			if (usecustomconfig) {
				String newconfigfile = TCDBprefs.getString(TCDBPreferences.KEY_CONFIGFILE);
				arglist.add("-C" + newconfigfile);
			}

			// Set up the External Command
			ExternalCommandLauncher TCDB = new ExternalCommandLauncher(command, arglist, cwd);
			TCDB.redirectErrorStream(true);

			MessageConsole console = null;
			// Set the Console (if requested by the user in the preferences)
			if (fPrefsStore.getBoolean(TCDBPreferences.KEY_USECONSOLE) || forceconsole) {
				console = TC32Plugin.getDefault().getConsole("Telink Binary Loader Console");
				TCDB.setConsole(console);
			}

			ICommandOutputListener outputlistener = new OutputListener(monitor);
			TCDB.setCommandOutputListener(outputlistener);

		
			// Run TCDB
			try {
				fAbortReason = null;
				int result = TCDB.launch(new SubProgressMonitor(monitor, 200));
				
		        


				// Test if TCDB was aborted
				if (fAbortReason != null) {
					throw new TCDBException(fAbortReason, fAbortLine);
				}

				if (result == -1) {
					throw new TCDBException(Reason.USER_CANCEL, "");
				}
			} catch (IOException e) {
				// Something didn't work while running the external command
				throw new TCDBException(Reason.NO_TCDB_FOUND,
						"Cannot run TCDB executable. Please check the TC32 path preferences.", e);
			}

			// Everything was fine: get the ooutput from TCDB and return it
			// to the caller
			List<String> stdout = TCDB.getStdOut();

			monitor.worked(10);
            


			return stdout;
		} finally {
			//monitor.done();//JW need reboot mcu
			//fLastTCDBFinish = System.currentTimeMillis();
		}
	}



	/**
	 * Convenience method to print a message to the given stream. This method checks that the stream
	 * exists.
	 * 
	 * @param ostream
	 * @param message
	 * @throws IOException
	 */
	//private void writeOutput(IOConsoleOutputStream ostream, String message) throws IOException {
	//	if (ostream != null) {
	//		ostream.write(message);
	//	}
	//}

	/**
	 * Get the path to the System temp directory.
	 * 
	 * @return <code>IPath</code>
	 */
	//private IPath getTempDir() {

	//	String tmpdir = System.getProperty("java.io.tmpdir");
	//	return new Path(tmpdir);
	//}

	/**
	 * The Reason code why TCDB was aborted (or <code>null</code> if TCDB finished normally)
	 */
	protected volatile Reason	fAbortReason;

	/** The line from the TCDB output that caused the abort */
	protected String			fAbortLine;

	/**
	 * Internal class to listen to the output of TCDB and cancel TCDB if the certain key
	 * Strings appears in the output.
	 * <p>
	 * They are:
	 * <ul>
	 * <li><code>timeout</code></li>
	 * <li><code>Can't open device</code></li>
	 * <li><code>can't open config file</code></li>
	 * <li><code>Can't find programmer id</code></li>
	 * <li><code>TC32 Part ???? not found</code></li>
	 * </ul>
	 * </p>
	 * <p>
	 * Once any of these Strings is found in the output the associated Reason is set and TCDB is
	 * aborted via the ProgressMonitor.
	 * </p>
	 */
	private class OutputListener implements ICommandOutputListener {

		private final IProgressMonitor	fProgressMonitor;

		public OutputListener(IProgressMonitor monitor) {
			fProgressMonitor = monitor;
		}

		// private Reason fAbortReason;
		// private String fAbortLine;

		/*
		 * (non-Javadoc)
		 * @see
		 * de.innot.avreclipse.core.toolinfo.ICommandOutputListener#init(org.eclipse.core.runtime
		 * .IProgressMonitor)
		 */
		public void init(IProgressMonitor monitor) {
			// fProgressMonitor = monitor;
			// fAbortLine = null;
			// fAbortReason = null;
		}

		public void handleLine(String line, StreamSource source) {

			boolean abort = false;

			if (line.contains("timeout")) {
				abort = true;
				fAbortReason = Reason.TIMEOUT;
			} else if (line.contains("can't open device")) {
				abort = true;
				fAbortReason = Reason.PORT_BLOCKED;
			} else if (line.contains("can't open config file")) {
				abort = true;
				fAbortReason = Reason.CONFIG_NOT_FOUND;
			} else if (line.contains("Can't find programmer id")) {
				abort = true;
				fAbortReason = Reason.UNKNOWN_PROGRAMMER;
			} else if (line.contains("no programmer has been specified")) {
				abort = true;
				fAbortReason = Reason.NO_PROGRAMMER;
			} else if (line.matches("TC32 Part.+not found")) {
				abort = true;
				fAbortReason = Reason.UNKNOWN_MCU;
			} else if (line.endsWith("execution aborted")) {
				abort = true;
				fAbortReason = Reason.USER_CANCEL;
			} else if (line.contains("usbdev_open")) {
				abort = true;
				fAbortReason = Reason.NO_USB;
			} else if (line.contains("failed to sync with")) {
				abort = true;
				fAbortReason = Reason.SYNC_FAIL;
			} else if (line.contains("initialization failed")) {
				// don't set the abort flag so that the progress monitor does not get canceled. This
				// is a hack to make the #getAttachedMCU() method work.
				fAbortLine = line;
				fAbortReason = Reason.INIT_FAIL;
			} else if (line.contains("NO_TARGET_POWER")) {
				abort = true;
				fAbortReason = Reason.NO_TARGET_POWER;
			} else if (line.contains("can't set buffers for")) {
				abort = true;
				fAbortReason = Reason.INVALID_PORT;
			} else if (line.contains("error in USB receive")) {
				abort = true;
				fAbortReason = Reason.USB_RECEIVE_ERROR;
			}
			if (abort) {
				fProgressMonitor.setCanceled(true);
				fAbortLine = line;
			}
		}

		/*
		 * (non-Javadoc)
		 * @see de.innot.avreclipse.core.toolinfo.ICommandOutputListener#getAbortLine()
		 */
		public String getAbortLine() {
			return fAbortLine;
		}

		/*
		 * (non-Javadoc)
		 * @see de.innot.avreclipse.core.toolinfo.ICommandOutputListener#getAbortReason()
		 */
		public Reason getAbortReason() {
			return fAbortReason;
		}

	}

	/**
	 * Container class for TCDB configuration entries.
	 * <p>
	 * This class is stores the four informations that TCDB supplies about a Programming device
	 * or a MCU part:
	 * </p>
	 * <ul>
	 * <li>{@link #TCDBid} = TCDB internal id</li>
	 * <li>{@link #description} = Human readable description</li>
	 * <li>{@link #configfile} = Path to the TCDB configuration file which declares this
	 * programmer or part</li>
	 * <li>{@link #linenumber} = Line number within the configuration file where the definition
	 * starts</li>
	 * </ul>
	 * 
	 */
	public static class ConfigEntry {
		/** TCDB internal id for this entry */
		public String	TCDBid;

		/** (Human readable) description of this entry */
		public String	description;

		/** Path to the configuration file which contains the definition */
		public IPath	configfile;

		/** line number of the start of the definition */
		public int		linenumber;
	}

	private class ProgrammerType implements IProgrammer {

		private String			fTCDBId;

		private String			fDescription;

		private HostInterface	fHostInterface[];

		private TargetInterface	fTargetInterface;


		protected ProgrammerType(String id) {
			fTCDBId = id;
		}

		/*
		 * (non-Javadoc)
		 * @see de.innot.avreclipse.core.targets.IProgrammerType#getId()
		 */
		public String getId() {
			return fTCDBId;
		}

		/*
		 * (non-Javadoc)
		 * @see de.innot.avreclipse.core.targets.IProgrammerType#getDescription()
		 */
		public String getDescription() {
			if (fDescription == null) {
				try {
					ConfigEntry entry = getProgrammerInfo(fTCDBId);
					fDescription = entry.description;
				} catch (TCDBException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					fDescription = "";
				}
			}
			return fDescription;
		}

		/*
		 * (non-Javadoc)
		 * @see de.innot.avreclipse.core.targets.IProgrammer#getAdditionalInfo()
		 */
		public String getAdditionalInfo() {

			try {
				ConfigEntry entry = getProgrammerInfo(fTCDBId);
				String addinfo = getConfigDetailInfo(entry);
				return "TCDB.conf entry for this programmer:\n\n" + addinfo;
			} catch (TCDBException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return "";
		}

		/*
		 * (non-Javadoc)
		 * @see de.innot.avreclipse.core.targets.IProgrammerType#getHostInterface()
		 */
		public HostInterface[] getHostInterfaces() {
			if (fHostInterface == null) {
				String type = getType();
				fHostInterface = TCDB.getHostInterfaces(fTCDBId, type);
			}

			return fHostInterface;
		}

		/*
		 * (non-Javadoc)
		 * @see de.innot.avreclipse.core.targets.IProgrammerType#getTargetInterfaces()
		 */
		public TargetInterface getTargetInterface() {
			if (fTargetInterface == null) {
				String type = getType();
				fTargetInterface = getInterface(fTCDBId, type);
			}
			return fTargetInterface;
		}

	}

	public Set<String> getMCUList() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public String getType() {
		// TODO Auto-generated method stub
		return "FlashProgrammer";
	}

	public boolean hasMCU(String mcuid) {
		// TODO Auto-generated method stub
		return true;
	}

	public String getMCUInfo(String mcuid) {
		// TODO Auto-generated method stub
		return null;
	}
}
