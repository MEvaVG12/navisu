/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bzh.terrevirtuelle.navisu.bathymetry.db.impl;

import bzh.terrevirtuelle.navisu.app.drivers.databasedriver.DatabaseDriver;
import bzh.terrevirtuelle.navisu.app.guiagent.GuiAgentServices;
import bzh.terrevirtuelle.navisu.app.guiagent.geoview.GeoViewServices;
import bzh.terrevirtuelle.navisu.app.guiagent.layers.LayersManagerServices;
import bzh.terrevirtuelle.navisu.app.guiagent.layertree.LayerTreeServices;
import bzh.terrevirtuelle.navisu.bathymetry.controller.eventsProducer.BathymetryEventProducerServices;
import bzh.terrevirtuelle.navisu.bathymetry.db.BathymetryDB;
import bzh.terrevirtuelle.navisu.bathymetry.db.BathymetryDBServices;
import bzh.terrevirtuelle.navisu.bathymetry.db.impl.controller.BathymetryDBController;
import bzh.terrevirtuelle.navisu.core.view.geoview.worldwind.impl.GeoWorldWindViewImpl;
import bzh.terrevirtuelle.navisu.database.relational.DatabaseServices;
import bzh.terrevirtuelle.navisu.domain.bathymetry.model.Bathymetry;
import bzh.terrevirtuelle.navisu.domain.geometry.Point3D;
import bzh.terrevirtuelle.navisu.domain.geometry.Point3Df;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.capcaval.c3.component.ComponentState;
import org.capcaval.c3.component.annotation.UsedService;
import org.postgis.PGgeometry;

/**
 * @date 13 mars 2015
 * @author Serge Morvan
 */
public class BathymetryDBImpl
        implements BathymetryDB, BathymetryDBServices, DatabaseDriver, ComponentState {

    protected static final Logger LOGGER = Logger.getLogger(BathymetryDBImpl.class.getName());
    final String NAME = "Bathy";
    final String LAYER_NAME = "BathyShom";
    final String LIMIT = "100";
    @UsedService
    GuiAgentServices guiAgentServices;
    @UsedService
    GeoViewServices geoViewServices;
    @UsedService
    LayersManagerServices layersManagerServices;
    @UsedService
    LayerTreeServices layerTreeServices;
    @UsedService
    DatabaseServices databaseServices;
    @UsedService
    BathymetryEventProducerServices bathymetryEventProducerServices;
    private String dataFileName;
    private Connection connection;
    private PreparedStatement preparedStatement;
    private Statement statement;
    private List<Point3Df> points3df;
    private List<Point3D> points3d;
    protected WorldWindow wwd;
    protected RenderableLayer layer;
    protected static final String GROUP = "Bathymetry data";
    double longitude;
    double latitude;
    NumberFormat nf4 = new DecimalFormat("0.0000");
    NumberFormat nf1 = new DecimalFormat("0.0");
    int i = 0;
    BathymetryDBController bathymetryDBController;

    @SuppressWarnings("unchecked")
    @Override
    public void componentInitiated() {

        points3df = new ArrayList<>();
        points3d = new ArrayList<>();
        bathymetryDBController = BathymetryDBController.getInstance();
        bathymetryDBController.setBathymetryDB(this);
        wwd = GeoWorldWindViewImpl.getWW();
      //  layer = new RenderableLayer();
       // layer.setName(LAYER_NAME);
      //  geoViewServices.getLayerManager().insertGeoLayer(GeoLayer.factory.newWorldWindGeoLayer(layer));
     // geoViewServices.getLayerManager().
     layer=layersManagerServices.getLayer(GROUP, LAYER_NAME);
    //   layerTreeServices.addGeoLayer(GROUP, GeoLayer.factory.newWorldWindGeoLayer(layer));

    }

    @Override
    public void componentStarted() {
    }

    @Override
    public void componentStopped() {
    }

    @Override
    public Connection connect(String dbName, String hostName, String protocol, String port,
            String driverName, String userName, String passwd,
            String dataFileName) {
        this.dataFileName = dataFileName;

        this.connection = databaseServices.connect(dbName, hostName, protocol, port, driverName, userName, passwd);
        bathymetryDBController.setConnection(connection);

        try {
            try {
                ((org.postgresql.PGConnection) connection).addDataType("geography", Class.forName("org.postgis.PGgeometry"));
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (SQLException ex) {
            Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        String sql = "INSERT INTO bathy (coord, elevation) "
                + "VALUES ( ST_SetSRID(ST_MakePoint(?, ?), 4326), ?);";
        try {
            preparedStatement = connection.prepareStatement(sql);
        } catch (SQLException ex) {
            Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, ex.toString(), ex);
        }
        create();
        return connection;
    }

    @Override
    public Connection connect(String dbName, String hostName, String protocol, String port,
            String driverName, String userName, String passwd) {
        System.out.println(dbName + " " + hostName + " " + protocol + " " + port + " " + driverName + " " + userName + " " + passwd);
        this.connection = databaseServices.connect(dbName, hostName, protocol, port, driverName, userName, passwd);
        bathymetryDBController.setConnection(connection);
        try {
            try {
                ((org.postgresql.PGConnection) connection).addDataType("geometry", Class.forName("org.postgis.PGgeography"));
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (SQLException ex) {
            Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return connection;
    }

    @Override
    public void create() {
        /*
        CREATE TABLE testgeog(gid serial PRIMARY KEY, the_geog geography(POINT,4326) );
         */
        guiAgentServices.getJobsManager().newJob("retrieveAll", (progressHandle) -> {
        String query = "DROP TABLE IF EXISTS  bathy; "
                + "CREATE TABLE bathy("
            //    + "gid SERIAL PRIMARY KEY,"
                + "coord GEOGRAPHY(POINT, 4326), "
                + "elevation FLOAT); ";
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException ex) {
            Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, ex.toString(), ex);
        }

        read();
        insert();
        createIndex();
        // retrieve(-5.991000175476074, 52.900001525878906);
        retrieveAll();
         });
    }

    public final void read() {
        try {

            points3df = Files.lines(new File(dataFileName).toPath())
                    .map(line -> line.trim())
                   // .map(line -> line.split("\t"))
                    .map(line -> line.split(" "))
                    .map(tab -> new Point3Df(Float.parseFloat(tab[0]),
                    Float.parseFloat(tab[1]),
                    Float.parseFloat(tab[2])))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public final void insert() {

        points3df.stream().forEach((p) -> {
            try {
                preparedStatement.setDouble(1, p.getLon());
                preparedStatement.setDouble(2, p.getLat());
                preparedStatement.setDouble(3, p.getElevation());
                preparedStatement.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, ex.toString(), ex);
            }
        });
    }

    public final void createIndex() {
        try {
            connection.createStatement().executeQuery("CREATE INDEX bathyIndex ON bathy USING GIST (coord)");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, ex.toString(), ex);
        }
    }

    public final List<Point3D> retrieve(double lat, double lon) {
        PGgeometry geom;
        ResultSet r0;
        ResultSet r1;
        points3d.clear();
        layer.removeAllRenderables();

        try {

            r0 = connection.createStatement().executeQuery(
                    "SELECT coord,elevation "
                    + "FROM bathy "
                    + "ORDER BY coord <-> ST_SetSRID("
                    + "ST_MakePoint(" + Double.toString(lon) + ", " + Double.toString(lat) + "), 4326) "
                    + "LIMIT " + LIMIT);
            while (r0.next()) {

                geom = (PGgeometry) r0.getObject(1);
                longitude = geom.getGeometry().getFirstPoint().getX();
                latitude = geom.getGeometry().getFirstPoint().getY();
                r1 = connection.createStatement().executeQuery(
                        "SELECT ST_DISTANCE("
                        + "ST_SetSRID(ST_MakePoint(" + longitude
                        + ", " + latitude + "), 4326)::geography,"
                        + "ST_SetSRID(ST_MakePoint(" + Double.toString(lon)
                        + ", " + Double.toString(lat) + "), 4326)::geography"
                        + ");");
                while (r1.next()) {
                    if ((Double) r1.getObject(1) < 900.0) {
                        points3d.add(new Point3D(latitude, longitude, r0.getDouble(2)));
                        PointPlacemark pointPlacemark = createSounding(latitude, longitude, r0.getDouble(2));
                        layer.addRenderable(pointPlacemark);
                    }
                }
            }

        } catch (SQLException ex) {
            Logger.getLogger(BathymetryDBImpl.class.getName()).log(Level.SEVERE, ex.toString(), ex);
        }
        bathymetryEventProducerServices.setBathymetry(new Bathymetry(points3d));
        return points3d;
    }

    public final void retrieveAll() {
        guiAgentServices.getJobsManager().newJob("retrieveAll", (progressHandle) -> {
            PGgeometry geom;
            double depth;
            try {
                ResultSet r = connection.createStatement().executeQuery("SELECT  coord, elevation FROM bathy");
                while (r.next()) {
                    geom = (PGgeometry) r.getObject(1);
                    //   System.out.print("geom " + geom.getGeometry());
                    depth = r.getFloat(2);
                    //  System.out.println("  depth " + depth);

                    layer.addRenderable(createSounding(geom.getGeometry().getFirstPoint().getX(),
                            geom.getGeometry().getFirstPoint().getY(),
                            depth));
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, ex.toString(), ex);
            }
        });
    }

    @Override
    public void close() {
        databaseServices.close();
    }

    public void view(List<Point3Df> points) {

    }

    public PointPlacemark createSounding(double lat, double lon, double depth) {
        PointPlacemarkAttributes attributes = new PointPlacemarkAttributes();
        attributes.setUsePointAsDefaultImage(true);
//attributes.setScale(10.0);
        PointPlacemark placemark = new PointPlacemark(Position.fromDegrees(lat, lon, 10));
        placemark.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        placemark.setEnableBatchPicking(true);
        placemark.setAttributes(attributes);
        String label = "Lat : " + nf4.format(lat) + "°\n"
                + "Lon : " + nf4.format(lon) + "°\n"
                + "Depth : " + nf1.format(depth) + "m";
        placemark.setValue(AVKey.DISPLAY_NAME, label);
      //  System.out.println("placemark " + placemark.getPosition().getLatitude().degrees);
      //  System.out.println(label);
        layer.addRenderable(placemark);
        return placemark;
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean canOpen(String dbName) {

        return dbName.equalsIgnoreCase(NAME);
    }

    @Override
    public DatabaseDriver getDriver() {
        return this;
    }
}
