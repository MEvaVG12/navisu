/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bzh.terrevirtuelle.navisu.instruments.nmea.n2k.logger;

import bzh.terrevirtuelle.navisu.app.drivers.instrumentdriver.InstrumentDriver;
import org.capcaval.c3.component.ComponentService;

/**
 * @date 3 mars 2015
 * @author Serge Morvan
 */
public interface N2KLoggerServices extends ComponentService {

    void on(String ... files);

    default void off() {
    }

    boolean isOn();

    boolean canOpen(String category);

    InstrumentDriver getDriver();
}
