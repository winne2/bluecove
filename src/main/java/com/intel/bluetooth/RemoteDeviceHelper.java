/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2006-2007 Vlad Skarzhevskyy
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  @version $Id$
 */
package com.intel.bluetooth;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.microedition.io.Connection;

import com.intel.bluetooth.WeakVectorFactory.WeakVector;

/**
 * Implementation of RemoteDevice.
 * 
 * Instance of RemoteDevice can be created by User.
 * BlueCove should use only RemoteDeviceHelper class to create RemoteDevice instances.
 *  
 * <p>
 * <b><u>Your application should not use this class directly.</u></b>
 * 
 * @author vlads
 */
public abstract class RemoteDeviceHelper {
	
	private static class RemoteDeviceWithExtendedInfo extends RemoteDevice {
		
		String name;
		
		long addressLong;
		
		private Hashtable stackAttributes;
		
		private boolean paired;
		
		/**
		 * Connections can be discarded by the garbage collector.
		 */
		private WeakVector connections;
		
		private RemoteDeviceWithExtendedInfo(long address, String name) {
			super(RemoteDeviceHelper.getBluetoothAddress(address));
			this.name = name;
			this.addressLong = address;
		}
		
		private void addConnection(Object connection) {
			if (connections == null) {
				connections = WeakVectorFactory.createWeakVector();
			}
			connections.addElement(connection);
			DebugLog.debug("connection open, open now", connections.size());
		}
		
		private void removeConnection(Object connection) {
			if (connections == null) {
				return;
			}
			connections.removeElement(connection);
			DebugLog.debug("connection closed, open now", connections.size());
		}
		
		private void setStackAttributes(Object key, Object value) {
			if (stackAttributes == null) {
				stackAttributes = new Hashtable();
			}
			if (value == null) {
				stackAttributes.remove(key);
			} else {
				stackAttributes.put(key, value);
			}
		}
		
		private Object getStackAttributes(Object key) {
			if (stackAttributes == null) {
				return null;
			}
			return stackAttributes.get(key);
		}
		
		public String toString() {
			return super.getBluetoothAddress();
		}
		
		int connectionsCount() {
			if (connections == null) {
				return 0;
			}
			return connections.size();
		}

		boolean hasConnections() {
			return (connectionsCount() != 0);
		}

		/* (non-Javadoc)
		 * @see javax.bluetooth.RemoteDevice#isAuthenticated()
		 */
		public boolean isAuthenticated() {
			if (!hasConnections()) {
				DebugLog.debug("no connections, Authenticated = false");
				return false;
			}
			return (((BluetoothConnectionAccess)connections.firstElement()).getSecurityOpt() != ServiceRecord.NOAUTHENTICATE_NOENCRYPT);
		}
		
		/* (non-Javadoc)
		 * @see javax.bluetooth.RemoteDevice#isEncrypted()
		 */
		public boolean isEncrypted() {
			if (!hasConnections()) {
				return false;
			}
			return (((BluetoothConnectionAccess)connections.firstElement()).getSecurityOpt() == ServiceRecord.AUTHENTICATE_ENCRYPT);
		}
		
		/* (non-Javadoc)
		 * @see javax.bluetooth.RemoteDevice#isTrustedDevice()
		 */
		public boolean isTrustedDevice() {
			return paired;
		}
	}
	
	private static Hashtable devicesCashed = new Hashtable();
	
	private RemoteDeviceHelper() {
		
	}

	private static RemoteDeviceWithExtendedInfo getCashedDeviceWithExtendedInfo(long address) {
		Object key = new Long(address);
		return (RemoteDeviceWithExtendedInfo)devicesCashed.get(key);
	}
	
	static RemoteDevice getCashedDevice(long address) {
		return getCashedDeviceWithExtendedInfo(address);
	}

	static RemoteDevice createRemoteDevice(long address, String name, boolean paired) {
		RemoteDeviceWithExtendedInfo dev = getCashedDeviceWithExtendedInfo(address);
		if (dev == null) {
			dev = new RemoteDeviceWithExtendedInfo(address, name);
			devicesCashed.put(new Long(address), dev);
			DebugLog.debug0x("new devicesCashed", address);
		} else if (!Utils.isStringSet(dev.name)) {
			// New name found
			dev.name = name;
		} else if (Utils.isStringSet(name)) {
			// Update name if changed
			dev.name = name;
		}
		if (paired) {
			dev.paired = paired;
		}
		return dev;
	}
	
	private static RemoteDeviceWithExtendedInfo remoteDeviceImpl(RemoteDevice device) {
		return (RemoteDeviceWithExtendedInfo)createRemoteDevice(device);
	}
	
	static RemoteDevice createRemoteDevice(RemoteDevice device) {
		if (device instanceof RemoteDeviceWithExtendedInfo) {
			return device;
		} else {
			return createRemoteDevice(getAddress(device), null, false);
		}
	}
	
	public static String getFriendlyName(RemoteDevice device, long address, boolean alwaysAsk) throws IOException {
		String name = null;
		if (!(device instanceof RemoteDeviceWithExtendedInfo)) {
			device = createRemoteDevice(device);
		}
		name = ((RemoteDeviceWithExtendedInfo)device).name;
		if (alwaysAsk || (!Utils.isStringSet(name))) {
			name = BlueCoveImpl.instance().getBluetoothStack().getRemoteDeviceFriendlyName(address);
			if (Utils.isStringSet(name)) {
				((RemoteDeviceWithExtendedInfo)device).name = name;
			} else {
				throw new IOException("Can't query remote device");
			}
		}
		return name;
	}
	
	public static RemoteDevice getRemoteDevice(Connection conn) throws IOException {
		if (!(conn instanceof BluetoothConnectionAccess)) {
			throw new IllegalArgumentException("Not a Bluetooth connection " + conn.getClass().getName());
		}
		return createRemoteDevice(((BluetoothConnectionAccess)conn).getRemoteAddress(), null, false);
	}
	
	/* (non-Javadoc)
	 * @see javax.bluetooth.DiscoveryAgent#retrieveDevices(int)
	 */
	public static RemoteDevice[] retrieveDevices(int option) {
		switch (option) {
		case DiscoveryAgent.PREKNOWN:
			if (devicesCashed.size() == 0) {
				// Spec: null if no devices meet the criteria
				return null;
			}
			Vector devicesPaired = new Vector();
			for(Enumeration en = devicesCashed.elements(); en.hasMoreElements(); ) {
				RemoteDeviceWithExtendedInfo d = (RemoteDeviceWithExtendedInfo)en.nextElement();
				if (d.isTrustedDevice()) {
					devicesPaired.addElement(d);
				}
			}
			if (devicesPaired.size() == 0) {
				// Spec: null if no devices meet the criteria
				return null;
			}
			RemoteDevice[] pdevices = new RemoteDevice[devicesPaired.size()];
			int i = 0;
			for(Enumeration en = devicesPaired.elements(); en.hasMoreElements(); ) {
				pdevices[i++] = (RemoteDevice)en.nextElement();
			}
			return pdevices;
		case DiscoveryAgent.CACHED:
			if (devicesCashed.size() == 0) {
				// Spec: null if no devices meet the criteria
				return null;
			}
			RemoteDevice[] devices = new RemoteDevice[devicesCashed.size()];
			int k = 0;
			for(Enumeration en = devicesCashed.elements(); en.hasMoreElements(); ) {
				devices[k++] = (RemoteDevice)en.nextElement();
			}
			return devices; 
		default:
			throw new IllegalArgumentException("invalid option");
		}
	}

	public static int openConnections() {
		int c = 0;
		for (Enumeration en = devicesCashed.elements(); en.hasMoreElements();) {
			c += ((RemoteDeviceWithExtendedInfo) en.nextElement()).connectionsCount();
		}
		return c;
	}
	
	public static String getBluetoothAddress(String address) {
		String s = address.toUpperCase();
		return "000000000000".substring(s.length()) + s;
	}
	
	static String getBluetoothAddress(long address) {
		return getBluetoothAddress(Utils.toHexString(address));
	}
	
	public static long getAddress(String bluetoothAddress) {
		if(bluetoothAddress.indexOf('-') != -1) {
			throw new IllegalArgumentException("Illegal bluetoothAddress {" + bluetoothAddress + "}");
		}
		try {
			return Long.parseLong(bluetoothAddress, 16);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Illegal bluetoothAddress {" + bluetoothAddress + "}");
		}
	}
	
	static long getAddress(RemoteDevice device) {
		if (device instanceof RemoteDeviceWithExtendedInfo) {
			return ((RemoteDeviceWithExtendedInfo)device).addressLong;
		} else {
			return getAddress(device.getBluetoothAddress());
		}
	}
	
	static void setStackAttributes(RemoteDevice device, Object key, Object value) {
		RemoteDeviceWithExtendedInfo devInfo = (RemoteDeviceWithExtendedInfo)createRemoteDevice(device);
		devInfo.setStackAttributes(key, value);
	}
	
	static Object getStackAttributes(RemoteDevice device, Object key) {
		RemoteDeviceWithExtendedInfo devInfo = null;
		if (device instanceof RemoteDeviceWithExtendedInfo) {
			devInfo = (RemoteDeviceWithExtendedInfo)device;
		} else {
			devInfo = getCashedDeviceWithExtendedInfo(getAddress(device));
		}
		
		if (devInfo != null) {
			return devInfo.getStackAttributes(key);
		} else {
			return null;
		}
	}
	
	public static void connected(BluetoothConnectionAccess connection) throws IOException {
		RemoteDeviceWithExtendedInfo device = (RemoteDeviceWithExtendedInfo)getRemoteDevice((Connection)connection);
		connection.setRemoteDevice(device);
		device.addConnection(connection);
	}
	
	public static void disconnected(BluetoothConnectionAccess connection) {
		RemoteDevice d = connection.getRemoteDevice();
		if (d != null) {
			((RemoteDeviceWithExtendedInfo)d).removeConnection(connection);
			connection.setRemoteDevice(null);
		}
	}
	
	/**
	 * Determines if this <code>RemoteDevice</code> has been
	 * authenticated.
	 * <P>
	 * A device may have been authenticated by this application
	 * or another application.  Authentication applies to an ACL link between
	 * devices and not on a specific L2CAP, RFCOMM, or OBEX connection.
	 * Therefore, if <code>authenticate()</code> is performed when an L2CAP
	 * connection is made to device A, then <code>isAuthenticated()</code> may
	 * return <code>true</code> when tested as part of making an RFCOMM
	 * connection to device A.
	 *
	 * @return <code>true</code> if this <code>RemoteDevice</code> has
	 * previously been authenticated; <code>false</code> if it has not
	 * been authenticated or there are no open connections between the
	 * local device and this <code>RemoteDevice</code>
	 */
	public static boolean isAuthenticated(RemoteDevice device) {
		return remoteDeviceImpl(device).isAuthenticated();
	}
	
	/**
	 * Determines if data exchanges with this <code>RemoteDevice</code>
	 * are currently being encrypted.
	 * <P>
	 * Encryption may have been previously turned on by this or another
	 * application.  Encryption applies to an ACL link
	 * between devices and not on a specific L2CAP, RFCOMM, or OBEX connection.
	 * Therefore, if <code>encrypt()</code> is performed with the
	 * <code>on</code> parameter set to <code>true</code> when an L2CAP
	 * connection is made to device A, then <code>isEncrypted()</code> may
	 * return <code>true</code> when tested as part of making an RFCOMM
	 * connection to device A.
	 *
	 * @return <code>true</code> if data exchanges with this
	 * <code>RemoteDevice</code> are being encrypted; <code>false</code>
	 * if they are not being encrypted, or there are no open connections
	 * between the local device and this <code>RemoteDevice</code>
	 */
	public static boolean isEncrypted(RemoteDevice device) {
		return remoteDeviceImpl(device).isEncrypted();
	}

	public static boolean isTrustedDevice(RemoteDevice device) {
		return remoteDeviceImpl(device).isTrustedDevice();
	}
}
