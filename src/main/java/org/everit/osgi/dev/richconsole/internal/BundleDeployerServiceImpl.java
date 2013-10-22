package org.everit.osgi.dev.richconsole.internal;

/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.util.tracker.BundleTracker;

public class BundleDeployerServiceImpl implements Closeable {

    private static class RefreshListener implements FrameworkListener {

        private final Condition refreshFinishCondition;

        private final AtomicBoolean refreshFinished;

        private final Lock refreshFinishLock;

        public RefreshListener(final AtomicBoolean refreshFinished, final Lock refreshFinishLock,
                final Condition refreshFinishCondition) {
            this.refreshFinished = refreshFinished;
            this.refreshFinishLock = refreshFinishLock;
            this.refreshFinishCondition = refreshFinishCondition;
        }

        @Override
        public void frameworkEvent(final FrameworkEvent event) {
            int eventType = event.getType();
            if ((eventType == FrameworkEvent.ERROR) || (eventType == FrameworkEvent.PACKAGES_REFRESHED)) {
                refreshFinishLock.lock();
                try {
                    refreshFinished.set(true);
                    Logger.info("Framework refresh finished with code "
                            + BundleUtil.convertFrameworkEventTypeCode(eventType));
                    refreshFinishCondition.signal();
                } finally {
                    refreshFinishLock.unlock();
                }
            } else {
                StringBuilder sb = new StringBuilder("Event caught during refreshing packages with data:");
                if (event.getBundle() != null) {
                    sb.append("\n\tBundle: ").append(event.getBundle().toString());
                }
                if (event.getSource() != null) {
                    sb.append("\n\tSource object: ").append(event.getSource().toString());
                }
                sb.append("\n\tEvent type: ").append(BundleUtil.convertFrameworkEventTypeCode(event.getType()));
                if (event.getThrowable() != null) {
                    Logger.error(sb.toString(), event.getThrowable());
                } else {
                    Logger.info(sb.toString());
                }
            }
        }
    }

    private class Tracker extends BundleTracker<Bundle> {

        private Map<String, List<Long>> bundleDataBySymbolicName = new ConcurrentHashMap<String, List<Long>>();

        public Tracker(final BundleContext context, final int stateMask) {
            super(context, stateMask, null);
        }

        @Override
        public Bundle addingBundle(final Bundle bundle, final BundleEvent event) {
            String symbolicName = bundle.getSymbolicName();
            List<Long> bundleIdList = bundleDataBySymbolicName.get(symbolicName);
            if (bundleIdList == null) {
                bundleIdList = new ArrayList<Long>();
                bundleDataBySymbolicName.put(symbolicName, bundleIdList);
            }
            bundleIdList.add(bundle.getBundleId());
            return super.addingBundle(bundle, event);
        }

        public List<Long> getBundleIdsBySymbolicName(final String symbolicName) {
            return bundleDataBySymbolicName.get(symbolicName);
        }

        @Override
        public void removedBundle(final Bundle bundle, final BundleEvent event, final Bundle object) {
            super.remove(bundle);
            String symbolicName = bundle.getSymbolicName();
            List<Long> list = bundleDataBySymbolicName.get(symbolicName);
            list.remove(bundle.getBundleId());
            if (list.size() == 0) {
                bundleDataBySymbolicName.remove(symbolicName);
            }
        }
    }

    private final BundleContext systemBundleContext;

    private final Tracker tracker;

    public BundleDeployerServiceImpl(final Bundle consoleBundle) {
        systemBundleContext = consoleBundle.getBundleContext().getBundle(0).getBundleContext();
        tracker =
                new Tracker(consoleBundle.getBundleContext(), Bundle.ACTIVE | Bundle.INSTALLED | Bundle.RESOLVED
                        | Bundle.STARTING | Bundle.STOPPING);
        tracker.open();

    }

    @Override
    public void close() throws IOException {
        tracker.close();
    }

    private Bundle deployBundle(final String bundleLocation, final BundleData bundleData, final Bundle originalBundle) {
        if (originalBundle != null) {
            if (originalBundle.getLocation().equals(bundleLocation)) {
                try {
                    if (originalBundle.getState() == Bundle.ACTIVE) {
                        Logger.info("Stopping already existing bundle " + originalBundle.toString());
                        originalBundle.stop();
                    }
                    Logger.info("Calling update on bundle " + originalBundle.toString());
                    originalBundle.update();
                    return originalBundle;
                } catch (BundleException e) {
                    Logger.error("Error during deploying bundle: " + bundleLocation, e);
                }
            } else {
                try {
                    Logger.info("Uninstalling Bundle " + originalBundle.getSymbolicName() + ":"
                            + originalBundle.getVersion().toString());
                    originalBundle.uninstall();
                    Logger.info("Installing bundle from '" + bundleLocation.toString() + "'");
                    Bundle installedBundle = systemBundleContext.installBundle(bundleLocation);
                    return installedBundle;
                } catch (BundleException e) {
                    Logger.error("Error during deploying bundle: " + bundleLocation, e);
                }
            }
        } else {
            try {
                Logger.info("Installing bundle from folder '" + bundleLocation + "'");
                Bundle installedBundle = systemBundleContext.installBundle(bundleLocation);
                return installedBundle;
            } catch (BundleException e) {
                Logger.error("Error during deploying bundle: " + bundleLocation, e);
            }
        }
        return null;
    }

    public void deployBundles(final List<File> fileObjects) {
        final AtomicBoolean refreshFinished = new AtomicBoolean(false);

        // Refresh classes must be initialized first because they will be not available if the richconsole re-deploys
        // itself
        Lock refreshFinishLock = new ReentrantLock();
        Condition refreshFinishCondition = refreshFinishLock.newCondition();
        FrameworkListener refreshListener =
                new RefreshListener(refreshFinished, refreshFinishLock, refreshFinishCondition);

        FrameworkWiring frameworkWiring = systemBundleContext.getBundle().adapt(FrameworkWiring.class);

        Map<String, BundleData> installableBundleByLocation = new LinkedHashMap<String, BundleData>();

        for (File fileObject : fileObjects) {
            File bundleLocation = null;
            if (fileObject.isDirectory()) {
                bundleLocation = new File(fileObject, "target/classes");
                if (!bundleLocation.exists()) {
                    Logger.warn("Hot deployment failed. There is no target/classes child folder found under "
                            + fileObject.getPath());
                    return;
                }
                File manifestFile = new File(bundleLocation, "META-INF/MANIFEST.MF");
                if (!manifestFile.exists()) {
                    Logger.warn("Hot deployment failed. Manifest file could not be found: " + manifestFile.getPath());
                    return;
                }

                try {
                    BundleData bundleData = BundleUtil.readBundleDataFromManifestFile(manifestFile);
                    installableBundleByLocation.put(BundleUtil.getBundleLocationByFile(bundleLocation), bundleData);
                } catch (IOException e) {
                    Logger.error("Could not deploy bundle from project location " + fileObject.toString(), e);
                    return;
                }
            } else {
                JarFile jarFile = null;
                try {
                    jarFile = new JarFile(fileObject);
                    Manifest manifest = jarFile.getManifest();
                    BundleData bundleData = BundleUtil.readBundleDataFromManifest(manifest);
                    bundleLocation = fileObject;
                    installableBundleByLocation.put(BundleUtil.getBundleLocationByFile(bundleLocation), bundleData);
                } catch (IOException e) {
                    Logger.error("Unrecognized file type", e);
                    return;
                } finally {
                    if (jarFile != null) {
                        try {
                            jarFile.close();
                        } catch (IOException e) {
                            Logger.error("Cannot close jar file: " + bundleLocation.getAbsolutePath(), e);
                        }
                    }
                }

            }
        }

        Map<BundleData, Bundle> originalBundleByNewBundleData = new HashMap<BundleData, Bundle>();
        for (Entry<String, BundleData> entry : installableBundleByLocation.entrySet()) {
            Bundle originalBundle = getExistingBundleBySymbolicName(entry.getKey(), entry.getValue());
            if (originalBundle != null) {
                originalBundleByNewBundleData.put(entry.getValue(), originalBundle);
            }
        }

        FrameworkStartLevel frameworkStartLevel = systemBundleContext.getBundle().adapt(FrameworkStartLevel.class);
        int originalFrameworkStartLevel = frameworkStartLevel.getStartLevel();

        int lowestStartLevel = getLowestStartLevel(originalBundleByNewBundleData.values(), originalFrameworkStartLevel);

        if (lowestStartLevel != originalFrameworkStartLevel) {
            BundleUtil.setFrameworkStartLevel(frameworkStartLevel, lowestStartLevel);
        }
        List<Bundle> installedBundles = new ArrayList<Bundle>();

        for (Entry<String, BundleData> entry : installableBundleByLocation.entrySet()) {
            Bundle installedBundle =
                    deployBundle(entry.getKey(), entry.getValue(), originalBundleByNewBundleData.get(entry.getValue()));
            if (installedBundle != null) {
                installedBundles.add(installedBundle);
            }
        }

        Logger.info("Calling refresh on OSGi framework. All packages on uninstalled bundles should be re-wired");

        frameworkWiring.refreshBundles(null, new FrameworkListener[] { refreshListener });

        refreshFinishLock.lock();
        try {
            while (!refreshFinished.get()) {
                refreshFinishCondition.await();
            }
        } catch (InterruptedException e) {
            Logger.error("Interrupting waiting for framework refresh", e);
        } finally {
            refreshFinishLock.unlock();
        }

        if (lowestStartLevel != originalFrameworkStartLevel) {
            Logger.info("Setting back startlevel");
            BundleUtil.setFrameworkStartLevel(frameworkStartLevel, originalFrameworkStartLevel);
        }

        for (Bundle bundle : installedBundles) {
            try {
                String fragmentHostHeader = bundle.getHeaders().get(Constants.FRAGMENT_HOST);
                if (fragmentHostHeader == null) {
                    Logger.info("Starting bundle " + bundle.toString());
                    bundle.start();
                }
            } catch (BundleException e) {
                Logger.error("Error starting bundle: " + bundle.toString(), e);
            }
        }
    }

    private Bundle getExistingBundleBySymbolicName(final String bundleLocation, final BundleData bundleData) {
        List<Long> existingBundleIds = tracker.getBundleIdsBySymbolicName(bundleData.getSymbolicName());
        Bundle selectedBundle = null;
        if (existingBundleIds != null) {
            if (existingBundleIds.size() == 1) {
                selectedBundle = systemBundleContext.getBundle(existingBundleIds.get(0));
            }
            Iterator<Long> iterator = existingBundleIds.iterator();
            while (iterator.hasNext() && (selectedBundle == null)) {
                Long existingBundleId = iterator.next();
                Bundle bundle = systemBundleContext.getBundle(existingBundleId);
                String existingBundleLocation = bundle.getLocation();

                if (existingBundleLocation.equals(bundleLocation)) {
                    selectedBundle = bundle;
                }

                if (selectedBundle == null) {
                    String existingBundleVersion = bundle.getVersion().toString();
                    if (existingBundleVersion.equals(bundleData.getVersion())) {
                        selectedBundle = bundle;
                    }
                }
            }
            if ((selectedBundle == null) && (existingBundleIds.size() > 0)) {
                selectedBundle = systemBundleContext.getBundle(existingBundleIds.get(0));
            }
        }
        return selectedBundle;
    }

    private int getLowestStartLevel(final Collection<Bundle> bundles, final int frameworkStartLevel) {
        int lowestStartLevel = frameworkStartLevel;
        for (Bundle bundle : bundles) {
            BundleStartLevel bundleStartLevel = bundle.adapt(BundleStartLevel.class);
            int startLevel = bundleStartLevel.getStartLevel();
            if (startLevel < lowestStartLevel) {
                lowestStartLevel = startLevel;
            }
        }
        return lowestStartLevel;
    }
}
