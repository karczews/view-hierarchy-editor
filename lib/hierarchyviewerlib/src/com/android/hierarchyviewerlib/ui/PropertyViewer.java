/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.hierarchyviewerlib.ui;

import com.android.ddmlib.Log;
import com.android.hierarchyviewerlib.device.DeviceBridge;
import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.device.ViewNode.Property;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.ITreeChangeListener;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.TreeColumnResizer;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.HelpListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PropertyViewer extends Composite implements ITreeChangeListener {
	
	private static final String PROPERTY_NAME_COLUMN = "Property";
	private static final String VALUE_COLUMN = "Value";
	private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
	
    private TreeViewModel mModel;

    private TreeViewer mTreeViewer;

    private Tree mTree;

    private DrawableViewNode mSelectedNode;

    private Font mSmallFont;

    private class ContentProvider implements ITreeContentProvider, ITableLabelProvider {

        public Object[] getChildren(Object parentElement) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && parentElement instanceof String) {
                    String category = (String) parentElement;
                    ArrayList<Property> returnValue = new ArrayList<Property>();
                    for (Property property : mSelectedNode.viewNode.properties) {
                        if (category.equals(ViewNode.MISCELLANIOUS)) {
                            if (property.name.indexOf(':') == -1) {
                                returnValue.add(property);
                            }
                        } else {
                            if (property.name.startsWith(((String) parentElement) + ":")) {
                                returnValue.add(property);
                            }
                        }
                    }
                    return returnValue.toArray(new Property[returnValue.size()]);
                }
                return new Object[0];
            }
        }

        public Object getParent(Object element) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && element instanceof Property) {
                    if (mSelectedNode.viewNode.categories.size() == 0) {
                        return null;
                    }
                    String name = ((Property) element).name;
                    int index = name.indexOf(':');
                    if (index == -1) {
                        return ViewNode.MISCELLANIOUS;
                    }
                    return name.substring(0, index);
                }
                return null;
            }
        }

        public boolean hasChildren(Object element) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && element instanceof String) {
                    String category = (String) element;
                    for (String name : mSelectedNode.viewNode.namedProperties.keySet()) {
                        if (category.equals(ViewNode.MISCELLANIOUS)) {
                            if (name.indexOf(':') == -1) {
                                return true;
                            }
                        } else {
                            if (name.startsWith(((String) element) + ":")) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        }

        public Object[] getElements(Object inputElement) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null && inputElement instanceof TreeViewModel) {
                    if (mSelectedNode.viewNode.categories.size() == 0) {
                        return mSelectedNode.viewNode.properties
                                .toArray(new Property[mSelectedNode.viewNode.properties.size()]);
                    } else {
                        return mSelectedNode.viewNode.categories
                                .toArray(new String[mSelectedNode.viewNode.categories.size()]);
                    }
                }
                return new Object[0];
            }
        }

        public void dispose() {
            // pass
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        	System.out.println("InputChanged" + oldInput + " new:" + newInput);
        }

        public Image getColumnImage(Object element, int column) {
            return null;
        }

        public String getColumnText(Object element, int column) {
            synchronized (PropertyViewer.this) {
                if (mSelectedNode != null) {
                    if (element instanceof String && column == 0) {
                        String category = (String) element;
                        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
                    } else if (element instanceof Property) {
                        if (column == 0) {
                            String returnValue = ((Property) element).name;
                            int index = returnValue.indexOf(':');
                            if (index != -1) {
                                return returnValue.substring(index + 1);
                            }
                            return returnValue;
                        } else if (column == 1) {
                            return ((Property) element).value;
                        }
                    }
                }
                return "";
            }
        }

        public void addListener(ILabelProviderListener listener) {
            // pass
        }

        public boolean isLabelProperty(Object element, String property) {
            // pass
            return false;
        }

        public void removeListener(ILabelProviderListener listener) {
            // pass
        }
    }

    public PropertyViewer(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FillLayout());
        mTreeViewer = new TreeViewer(this, SWT.FULL_SELECTION);
        
        mTree = mTreeViewer.getTree();
        mTree.setLinesVisible(true);
        mTree.setHeaderVisible(true);
        
        TreeViewerEditor.create(mTreeViewer,  new ColumnViewerEditorActivationStrategy(mTreeViewer), ColumnViewerEditor.DEFAULT);
        
        mTreeViewer.setCellEditors(new CellEditor[] {new TextCellEditor(mTree), new TextCellEditor(mTree)});
        //mTreeViewer.getColumnViewerEditor()
        mTreeViewer.setCellModifier(new ICellModifier() {
			@Override
			public void modify(Object item, String columnProperty, Object newValue) {
				TreeItem treeItem = (TreeItem) item;
				Property property = (Property) treeItem.getData();
				if (columnProperty == VALUE_COLUMN) {
					property.value = (String) newValue;
					mTreeViewer.update(treeItem, new String[] {PROPERTY_NAME_COLUMN, VALUE_COLUMN});
					doRefresh();
					doUpdateWindowProperty(property);
				}
//				Property property = (Property) treeItem.getData();
//				property.value = (String) newValue;
//				String [] affectedProperties = new String[] {VALUE_COLUMN}; //optional - might speedup update
//				treeItem.setData(property);
//				mTreeViewer.update(treeItem, null);
				System.out.println("modify: " + treeItem.getData().getClass() + " " + treeItem.getData().toString() + " newVal:" + newValue.toString() + " prop:" +columnProperty);
			}
			
			@Override
			public Object getValue(Object obj, String columnProperty) {
				ViewNode.Property property = (ViewNode.Property) obj;
				if (columnProperty == VALUE_COLUMN) {
					return property.value;
				} else if (columnProperty == PROPERTY_NAME_COLUMN) {
					return property.name;
				}
				return obj.toString();
			}
			
			@Override
			public boolean canModify(Object obj, String columnProperty) {
				if (columnProperty == VALUE_COLUMN) {
					return true;
				} else {
					return false;
				}
			}
		}); 
        
        mTreeViewer.setColumnProperties(new String[] {PROPERTY_NAME_COLUMN, VALUE_COLUMN}); 
        
        TreeColumn propertyColumn = new TreeColumn(mTree, SWT.NONE);
        propertyColumn.setText(PROPERTY_NAME_COLUMN);
        TreeColumn valueColumn = new TreeColumn(mTree, SWT.NONE);
        valueColumn.setText(VALUE_COLUMN);

        mModel = TreeViewModel.getModel();
        ContentProvider contentProvider = new ContentProvider();
        mTreeViewer.setContentProvider(contentProvider);
        mTreeViewer.setLabelProvider(contentProvider);
        mTreeViewer.setInput(mModel);
        mModel.addTreeChangeListener(this);

        loadResources();
        addDisposeListener(mDisposeListener);

        mTree.setFont(mSmallFont);

        new TreeColumnResizer(this, propertyColumn, valueColumn);

        addControlListener(mControlListener);

        
//        final MenuManager menuMgr = new MenuManager("#MenuManager");
//        final Menu menu = menuMgr.createContextMenu(mTreeViewer.getControl());
//        menuMgr.setRemoveAllWhenShown(true);
//        menuMgr.setVisible(true);
        
        treeChanged();
    }

    public void loadResources() {
        Display display = Display.getDefault();
        Font systemFont = display.getSystemFont();
        FontData[] fontData = systemFont.getFontData();
        FontData[] newFontData = new FontData[fontData.length];
        for (int i = 0; i < fontData.length; i++) {
            newFontData[i] = new FontData(fontData[i].getName(), 8, fontData[i].getStyle());
        }
        mSmallFont = new Font(Display.getDefault(), newFontData);
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeTreeChangeListener(PropertyViewer.this);
            mSmallFont.dispose();
        }
    };

    // If the window gets too small, hide the data, otherwise SWT throws an
    // ERROR.

    private ControlListener mControlListener = new ControlAdapter() {
        private boolean noInput = false;

        private boolean noHeader = false;

        @Override
        public void controlResized(ControlEvent e) {
            if (getBounds().height <= 20) {
                mTree.setHeaderVisible(false);
                noHeader = true;
            } else if (noHeader) {
                mTree.setHeaderVisible(true);
                noHeader = false;
            }
            if (getBounds().height <= 38) {
                mTreeViewer.setInput(null);
                noInput = true;
            } else if (noInput) {
                mTreeViewer.setInput(mModel);
                noInput = false;
            }
        }
    };

    public void selectionChanged() {
        synchronized (this) {
            mSelectedNode = mModel.getSelection();
        }
        doRefresh();
    }

    public void treeChanged() {
        synchronized (this) {
            mSelectedNode = mModel.getSelection();
        }
        doRefresh();
    }

    public void viewportChanged() {
        // pass
    }

    public void zoomChanged() {
        // pass
    }

    private void doRefresh() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                mTreeViewer.refresh();
            }
        });
    }
    
    private void doUpdateWindowProperty(final Property property) {
    	mExecutor.execute(new Runnable() {
    		public void run() {
    	    	DeviceBridge.updateWindowProperty(mSelectedNode.viewNode.window, property, mSelectedNode.viewNode.hashCode);
    		}
    	});
    }
}
