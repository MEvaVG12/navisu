/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bzh.terrevirtuelle.navisu.instruments.gps.plotter.impl.controller;

import bzh.terrevirtuelle.navisu.app.guiagent.GuiAgentServices;
import bzh.terrevirtuelle.navisu.app.guiagent.layers.LayersManagerServices;
import bzh.terrevirtuelle.navisu.app.guiagent.options.domain.Option;
import bzh.terrevirtuelle.navisu.app.guiagent.options.domain.OwnerShipOption;
import bzh.terrevirtuelle.navisu.app.guiagent.options.events.OwnerShipConfEvent;
import bzh.terrevirtuelle.navisu.core.view.geoview.worldwind.impl.GeoWorldWindViewImpl;
import bzh.terrevirtuelle.navisu.domain.nmea.model.nmea183.GGA;
import bzh.terrevirtuelle.navisu.domain.nmea.model.nmea183.RMC;
import bzh.terrevirtuelle.navisu.domain.nmea.model.nmea183.VTG;
import bzh.terrevirtuelle.navisu.domain.ship.model.Ship;
import bzh.terrevirtuelle.navisu.instruments.ais.aisradar.impl.controller.AisRadarController;
import bzh.terrevirtuelle.navisu.instruments.common.controller.GpsEventsController;
import bzh.terrevirtuelle.navisu.instruments.common.controller.GpsEventsListener;
import bzh.terrevirtuelle.navisu.instruments.common.controller.ShipController;
import bzh.terrevirtuelle.navisu.instruments.common.view.panel.TargetPanel;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.ogc.collada.ColladaRoot;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import bzh.terrevirtuelle.navisu.kml.KmlComponentServices;
import gov.nasa.worldwind.geom.Vec4;
import java.util.ArrayList;
import java.util.List;
import org.capcaval.c3.component.ComponentEventSubscribe;
import org.capcaval.c3.componentmanager.ComponentManager;

/**
 * NaVisu
 *
 * @date 7 mai 2015
 * @author Serge Morvan
 */
public class GpsPlotterController
        extends GpsEventsController {

    protected final String GROUP = "Navigation";
    protected GuiAgentServices guiAgentServices;
    protected KmlComponentServices kmlObjectServices;
    protected LayersManagerServices layersManagerServices;
    protected String name;
    protected WorldWindow wwd;
    protected RenderableLayer gpsLayer;
    protected TargetPanel targetPanel;
    protected Ship ownerShip;
    protected double initRotation;
    protected Properties properties;
    protected String CONFIG_FILE_NAME = System.getProperty("user.home") + "/.navisu/config/config.properties";
    protected ColladaRoot ownerShipView;
    protected CircularFifoQueue<RMC> sentenceQueue;
    protected double latitude = 0.0;
    protected double longitude = 0.0;
    protected CircularFifoQueue<Double> latQueue;
    protected CircularFifoQueue<Double> lonQueue;
    protected boolean first = true;
    int i = 0;
    protected List<GpsEventsListener> listeners;

    ComponentManager cm;
    ComponentEventSubscribe<OwnerShipConfEvent> oscES;

    public GpsPlotterController(LayersManagerServices layersManagerServices,
            GuiAgentServices guiAgentServices,
            KmlComponentServices kmlObjectServices,
            String name) {
        this(layersManagerServices, guiAgentServices, name);
        this.kmlObjectServices = kmlObjectServices;
        cm = ComponentManager.componentManager;
        oscES = cm.getComponentEventSubscribe(OwnerShipConfEvent.class);
    }

    public GpsPlotterController(LayersManagerServices layersManagerServices,
            GuiAgentServices guiAgentServices,
            String name) {
        this.layersManagerServices = layersManagerServices;
        this.guiAgentServices = guiAgentServices;
        this.name = name;
        listeners = new ArrayList<>();
        cm = ComponentManager.componentManager;
        oscES = cm.getComponentEventSubscribe(OwnerShipConfEvent.class);
    }

    public void init(boolean subscribe) {
        sentenceQueue = new CircularFifoQueue<>(6);
        wwd = GeoWorldWindViewImpl.getWW();
        gpsLayer = layersManagerServices.getLayer(GROUP, name);
        properties = new Properties();
        try {
            properties.load(new FileInputStream(CONFIG_FILE_NAME));
        } catch (IOException ex) {
            Logger.getLogger(AisRadarController.class.getName()).log(Level.SEVERE, null, ex);
        }
        addPanelController();
        addListeners();
        ownerShip = ShipController.createOwnerShip(properties);
        createTarget();
        if (subscribe == true) {
            subscribe();
        }
        oscES.subscribe(new OwnerShipConfEvent() {
            @Override
            public <T extends Option> void notifyConfMessageChanged(T d) {
                OwnerShipOption option = (OwnerShipOption) d;
                updateShipFromConfigurationPanel(option);
            }
        });
    }

    protected void addPanelController() {
        Platform.runLater(() -> {
            targetPanel = new TargetPanel(guiAgentServices, KeyCode.B, KeyCombination.CONTROL_DOWN);
            guiAgentServices.getScene().addEventFilter(KeyEvent.KEY_RELEASED, targetPanel);
            guiAgentServices.getRoot().getChildren().add(targetPanel);
            targetPanel.setScale(1.0);
            targetPanel.setVisible(false);
        });
    }

    protected void addListeners() {
        wwd.addSelectListener((SelectEvent event) -> {
            Object o = event.getTopObject();
            if (event.isLeftClick() && o != null) {
                if (o.getClass() == ColladaRoot.class) {
                    Object object = ((ColladaRoot) o).getField("Ship");
                    if (object != null && object.getClass() == Ship.class) {
                        Ship ship = (Ship) object;
                        updateShipPanel(ship);
                    }
                }
            }
        });
    }

    protected void updateShipPanel(Ship ship) {
        Platform.runLater(() -> {
            targetPanel.updatePanel(ship);
        });
    }

    protected void createTarget() {
        this.latitude = ownerShip.getLatitude();
        this.longitude = ownerShip.getLongitude();
        ownerShipView = kmlObjectServices.openColladaFile(gpsLayer, properties.getProperty("daeModelPath").trim());
        ownerShipView.setPosition(Position.fromDegrees(ownerShip.getLatitude(), ownerShip.getLongitude(), 1000.0));
        ownerShipView.setHeading(Angle.fromDegrees(ownerShip.getCog()));
        String scaleS;
        double scaleD;
        try {
            scaleS = properties.getProperty("scale");
            if (scaleS != null) {
                scaleD = Double.valueOf(scaleS.trim());
            } else {
                scaleD = 1.0;
            }
        } catch (NumberFormatException e) {
            scaleD = 1.0;
        }
        ownerShipView.setModelScale(new Vec4(scaleD));
        ownerShipView.setField("Ship", ownerShip);
    }

    public void updateTarget(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        ownerShip.setLatitude(latitude);
        ownerShip.setLongitude(longitude);
        ownerShipView.setHeading(Angle.fromDegrees(ownerShip.getCog()));
        ownerShipView.setPosition(Position.fromDegrees(ownerShip.getLatitude(), ownerShip.getLongitude(), 1000.0));

        updateTarget(ownerShip);
        wwd.redrawNow();
    }

    @Override
    protected void notifyNmeaMessage(GGA data) {
        updateTarget(data.getLatitude(), data.getLongitude());
    }

    @Override
    protected void notifyNmeaMessage(VTG data) {
        ownerShip.setCog(data.getCog());
        ownerShip.setSog(data.getSog());
        ownerShipView.setHeading(Angle.fromDegrees(ownerShip.getCog() + initRotation));
    }

    @Override
    protected void notifyNmeaMessage(RMC data) {
        ownerShip.setCog(data.getCog());
        ownerShip.setSog(data.getSog());
        updateTarget(data.getLatitude(), data.getLongitude());
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Ship getOwnerShip() {
        return ownerShip;
    }

    @Override
    public void updateTarget(Ship ship) {
        listeners.forEach((gpc) -> {
            gpc.updateTarget(ownerShip);
        });
    }

    public void updateShipFromConfigurationPanel(OwnerShipOption option) {

        try {
            ownerShipView.setModelScale(new Vec4(Double.parseDouble(option.getScale())));   
        } catch (NumberFormatException e) {
            // Nothing if no good input    
        }
        
        try {
            ownerShipView.setPosition(
                    new Position(Angle.fromDegrees(Double.parseDouble(option.getLatitude())),
                            Angle.fromDegrees(Double.parseDouble(option.getLongitude())),
                            10.0));
            ownerShip.setLatitude(Double.parseDouble(option.getLatitude()));
            ownerShip.setLongitude(Double.parseDouble(option.getLongitude()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }
        
        try {
            ownerShipView.setHeading(Angle.fromDegrees(Double.parseDouble(option.getCog())));
            ownerShip.setCog(Double.parseDouble(option.getCog()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }
        
        try {
            ownerShipView = kmlObjectServices.openColladaFile(gpsLayer, option.getDaeModelPath());
            ownerShipView.setPosition(
                    new Position(Angle.fromDegrees(Double.parseDouble(option.getLatitude())),
                            Angle.fromDegrees(Double.parseDouble(option.getLongitude())),
                            10.0));
            ownerShipView.setHeading(Angle.fromDegrees(Double.parseDouble(option.getCog())));
            ownerShipView.setModelScale(new Vec4(Double.parseDouble(option.getScale())));
        } catch (Exception e) {
            // Nothing if no good input
            System.out.println("e : " + e);
        }

        try {
            ownerShip.setSog(Double.parseDouble(option.getSog()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }
        try {
            ownerShip.setLength(Float.parseFloat(option.getLength()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }
        try {
            ownerShip.setWidth(Float.parseFloat(option.getWidth()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }
        try {
            ownerShip.setDraught(Float.parseFloat(option.getDraught()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }
        try {
            ownerShip.setShipType(Integer.parseInt(option.getShipType()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }
        try {
            ownerShip.setNavigationalStatus(Integer.parseInt(option.getNavigationalStatus()));
        } catch (NumberFormatException e) {
            // Nothing if no good input
        }

        ownerShip.setCountry(option.getCountry());
        ownerShip.setName(option.getCountry());
        ownerShip.setCallSign(option.getCallSign());
        wwd.redrawNow();
    }
}