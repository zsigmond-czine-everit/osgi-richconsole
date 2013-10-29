package org.everit.osgi.dev.richconsole.internal.settings;

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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import org.everit.osgi.dev.richconsole.RichConsoleService;

public class SettingsExtensionImpl {

    private RichConsoleService richConsoleService;

    private SettingsFrame settingsFrame;
    
    private JMenuItem menuItem; 

    public void close() {
        if (settingsFrame != null) {
            settingsFrame.dispose();
            settingsFrame = null;
        }
        richConsoleService.removeMenuItemFromContextMenu(menuItem);
        menuItem = null;
    }

    private synchronized SettingsFrame getOrCreateSettingsFrame() {
        if (settingsFrame == null) {
            settingsFrame = new SettingsFrame(richConsoleService.getConfigStore());
        }
        return settingsFrame;
    }

    public void init(final RichConsoleService richConsoleService) {
        this.richConsoleService = richConsoleService;
        this.menuItem = new JMenuItem("Main settings");
        menuItem.addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                menuItemFired();
            }
        });
        richConsoleService.addMenuItemToContextMenu(this.menuItem);
        
    }

    public void menuItemFired() {
        SettingsFrame tmpSettingsFrame = getOrCreateSettingsFrame();
        tmpSettingsFrame.setVisible(true);
        tmpSettingsFrame.requestFocus();
    }
}