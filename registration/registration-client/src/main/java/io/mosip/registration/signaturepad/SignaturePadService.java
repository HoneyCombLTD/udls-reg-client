package io.mosip.registration.signaturepad;

import de.signotec.stpad.api.*;
import de.signotec.stpad.api.events.*;
import de.signotec.stpad.api.exceptions.SigPadException;
import de.signotec.stpad.control.SignatureCanvas;
import de.signotec.stpad.control.SignatureJPanel;
import de.signotec.stpad.enums.ImageType;
import de.signotec.stpad.enums.SampleRate;
import de.signotec.stpad.enums.ScrollDirection;
import de.signotec.stpad.enums.SigPadAlign;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.device.SignaturePopUpViewController;
import javafx.embed.swing.SwingNode;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import org.mvel2.ast.Sign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

import static de.signotec.stpad.driver.Constants.*;
import static de.signotec.stpad.driver.Constants.SIGPAD_MODELTYPE_ALPHA_ETHERNET;


public class SignaturePadService implements DisconnectListener {


    /** Font used to render the text on the pad display. */
    private static final String FONT_NAME_PAD = "Arial";

    /** The width of the pen shown on the Sigma pad. */
    private static final int PEN_WIDTH_SIGMA_PAD = 2;
    /** The width of the pen shown in the graphics control element for the Sigma pad. */
    private static final int PEN_WIDTH_SIGMA_CONTROL = 2;
    /** The width of the pen shown on the Sigma pad. */
    private static final int PEN_WIDTH_ZETA_PAD = 2;
    /** The width of the pen shown in the graphics control element for the Zeta pad. */
    private static final int PEN_WIDTH_ZETA_CONTROL = 2;
    /** The width of the pen shown on the Omega pad. */
    private static final int PEN_WIDTH_OMEGA_PAD = 3;
    /** The width of the pen shown in the graphics control element for the Omega pad. */
    private static final int PEN_WIDTH_OMEGA_CONTROL = 3;
    /** The width of the pen shown on the Gamma pad. */
    private static final int PEN_WIDTH_GAMMA_PAD = 3;
    /** The width of the pen shown in the graphics control element for the Gamma pad. */
    private static final int PEN_WIDTH_GAMMA_CONTROL = 3;
    /** The width of the pen shown on the Delta pad. */
    private static final int PEN_WIDTH_DELTA_PAD = 3;
    /** The width of the pen shown in the graphics control element for the Delta pad. */
    private static final int PEN_WIDTH_DELTA_CONTROL = 3;
    /** The width of the pen shown on the Alpha pad. */
    private static final int PEN_WIDTH_ALPHA_PAD = 1;
    /** The width of the pen shown in the graphics control element for the Alpha pad. */
    private static final int PEN_WIDTH_ALPHA_CONTROL = 1;
    /** The width of the pen shown in the graphics control element for the Pen Display device. */
    private static final int PEN_WIDTH_PEN_DISPLAY_CONTROL_MAX = 3;
    private static final int PEN_WIDTH_PEN_DISPLAY_CONTROL_MIN = 1;

    /** The scroll speed of the disclaimer text in pixel per second for the Omega pad. */
    private static final int SCROLL_SPEED_OMEGA = 300;
    /** The scroll speed of the disclaimer text in pixel per second for the Gamma pad. */
    private static final int SCROLL_SPEED_GAMMA = 300;
    /** The scroll speed of the disclaimer text in pixel per second for the Delta pad. */
    private static final int SCROLL_SPEED_DELTA = 500;

    private static final boolean USE_CANVAS = false;


    private static final Logger LOGGER = LoggerFactory.getLogger(SignaturePadService.class);
    private SigPadDevice[] pads = new SigPadDevice[0];
    private SigPadApi sigPad = null;
    private SigPadFacade stpadNativeFacade = null;
    private DefaultListModel<String> listModelDevices;
    private ImageMemory signingPageMemory = null;
    private ImageMemory disclaimerPageMemory = null;
    private Component signatureComponent = null;
    private SignatureGraphics signatureGraphics = null;
    private BufferedImage imgOk;
    /** The cancel signature process button. */
    private BufferedImage imgCancel;
    /** The retry signature process button. */
    private BufferedImage imgRetry;
    private BufferedImage imgSignature;
    private SignaturePopUpViewController controller;
    private SwingNode swingNode;

    private static SignaturePadService instance;

    public static SignaturePadService getInstance(){
        if(instance==null){
            instance = new SignaturePadService();
        }
        return instance;
    }

    public boolean getDevices() {

        if(this.getSelectedPad()==null){
            LOGGER.info("searching devices....");
            try {
                listModelDevices = new DefaultListModel<>();
//            get native sign pad
                this.pads = getSigPadDevices();
                LOGGER.info("obtained devices array ....");
                if (this.pads.length == 0) {
                    // no pads connected/available
                    this.listModelDevices.addElement("No Devices");
                    this.listModelDevices.addElement("detected");
                } else {
                    // add signature pads to JList
                    for (SigPadDevice device : this.pads) {
                        this.listModelDevices.addElement(device.getModelName());
                    }
                    LOGGER.info("selected pad device {}",getSelectedPad());
                }
            }catch (Exception e){
                LOGGER.info("ERROR {}", e.getMessage());
            }finally {

            }
        }
        return getSelectedPad() != null;
    }

    private SigPadDevice[] getSigPadDevices() {
        try {
            LOGGER.info("....... getting sign pad devices .......");
            return getSigPadFacade().getSignatureDevices();
        } catch (SigPadException e) {
            LOGGER.info("Error while searching for pads with SigPadFacade: {}", e.getMessage());
            return new SigPadDevice[0];
        }
    }

    private void clearModelDevices() {
        this.listModelDevices.clear();
    }

    @Override
    public void disconnect() {
        cancelSignature();// no more calls to the pad
    }

    @Override
    public void handleError(SigPadException e) {

    }

    /**
     * Lazy initialization of the native SigPadFacade.
     *
     * @return Returns the facade.
     *
     * @throws SigPadException
     *             If unable to create the facade.
     */
    private SigPadFacade getSigPadFacade()
            throws SigPadException {

        if (this.stpadNativeFacade == null) {
            SigPadFacade facade = null;

            try {
                facade = SigPadFacade.getInstance();
                facade.initializeApi();
                this.stpadNativeFacade = facade;
            } catch (SigPadException e) {
                facade.finalizeApi();

                throw new SigPadException("unable to initialize SigPadFacade", e);
            }
        }

        return this.stpadNativeFacade;
    }

    /**
     * @return Returns the selected signature pad or <code>null</code>.
     */
    private SigPadDevice getSelectedPad() {
        if (this.pads.length == 0) {
            return null;
        }
        return this.pads[0];
    }

    /**
     * Cancels the signature procedure.
     */
    private void cancelSignature() {
        try {
            this.sigPad.cancelSignature();
        } catch (SigPadException e) {
            LOGGER.error(e.getMessage(), e);
        }
        finishSignature();
    }

    /**
     * Leaves the signature mode.
     */
    private void finishSignature() {
        try {
            this.sigPad.clearHotSpots();
            this.pads = new SigPadDevice[0];
        } catch (SigPadException e) {
            LOGGER.info( "{} \n finishSignature - clearHotSpots", e.getMessage());
        }
        closePad();
    }

    /**
     * Closes the connection to signature pad.
     */
    private void closePad() {
        // remove capture control
        if (this.swingNode != null) {
            controller.getGroupStackPane().getChildren().remove(this.swingNode);
        }
        if (this.sigPad != null) {

            try {
                // drop signature data
                this.sigPad.cancelSignature();

                // close connection
                this.sigPad.closeDevice();
            } catch (SigPadException e) {
                LOGGER.info(e.getMessage());
            }

            try {
                if (this.stpadNativeFacade != null) {
                    this.stpadNativeFacade.finalizeApi();
                }
            } catch (SigPadException e) {
                LOGGER.info(e.getMessage(), e);
            }

            this.sigPad = null;
        }
        SignaturePadService.instance = null;
    }


    /**
     * Opens the connection to the selected signature pad.
     */
    public void openPad(SignaturePopUpViewController controller, ImageView signatureImage) {
        this.controller = controller;
        final SigPadDevice device = getSelectedPad();
        if (device == null) {
            return; // exit method if no device is selected
        }
        this.sigPad = new SigPadApi(device);
        this.sigPad.addSigPadListener(new SigPadAdapter() {

            @Override
            public void errorOccurred(ErrorOccurredEvent event) {
                event.consume(); // the signoPAD-API should not log this error
                LOGGER.info("Error event received: " + event.cause.getMessage(), event.cause);
            }
        });

        // set sample rate & pen to default values and configure view
        float dpi = 0;
        float maxPenWidth = Constants.MAXIMUM_PEN_WIDTH;
        String displayImg = null;
        Point displayPos = null; // the position of the pad display

        try {
            if (device.isModelSigma()) {
                this.sigPad.openDevice(this);
                controller.setImage(signatureImage, RegistrationConstants.RES_IMG_DEVICE_SIGMA);
                dpi = 88;
                displayImg = controller.getImagePath(RegistrationConstants.RES_IMG_LOGO_SIGMA, false);
                displayPos = new Point(300, -900);
                maxPenWidth = PEN_WIDTH_SIGMA_CONTROL;
                this.sigPad.setSampleRate(SampleRate.HZ_250);
                this.sigPad.setSignaturePenWidth(PEN_WIDTH_SIGMA_PAD);
                this.disclaimerPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                this.signingPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                this.imgSignature = loadImage(controller.getImagePath(RegistrationConstants.RES_IMG_SIGNATURE_SIGMA, false));
                this.imgOk = loadImage(controller.getImagePath(RegistrationConstants.RES_IMG_BTN_OK_BW, false));
                this.imgCancel = loadImage(controller.getImagePath(RegistrationConstants.RES_IMG_BTN_CANCEL_BW, false));
            } else if (device.isModelZeta()) {
                this.sigPad.openDevice(this);
                controller.setImage(signatureImage, RegistrationConstants.RES_IMG_DEVICE_ZETA);
                dpi = 85;
                displayPos = new Point(96, 98);
                maxPenWidth = PEN_WIDTH_ZETA_CONTROL;
                this.sigPad.setSampleRate(SampleRate.HZ_250);
                this.sigPad.setSignaturePenWidth(PEN_WIDTH_ZETA_PAD);
                this.disclaimerPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                this.signingPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);

            } else if (device.isModelOmega()) {
                this.sigPad.openDevice(this);
                controller.setImage(signatureImage, RegistrationConstants.RES_IMG_DEVICE_OMEGA);
                dpi = 90;
                displayPos = new Point(76, 70);
                maxPenWidth = PEN_WIDTH_OMEGA_CONTROL;
                this.sigPad.setSampleRate(SampleRate.HZ_250);
                this.sigPad.setSignaturePenWidth(PEN_WIDTH_OMEGA_PAD, Color.BLUE);
                this.disclaimerPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                this.signingPageMemory = ImageMemory.requestPermanentStore(this.sigPad);
            } else if (device.isModelGamma()) {
                this.sigPad.openDevice(this);
                controller.setImage(signatureImage, RegistrationConstants.RES_IMG_DEVICE_GAMMA);
                dpi = 94;
                displayPos = new Point(55, 102);
                maxPenWidth = PEN_WIDTH_GAMMA_CONTROL;
                this.sigPad.setSampleRate(SampleRate.HZ_250);
                this.sigPad.setSignaturePenWidth(PEN_WIDTH_GAMMA_PAD, Color.BLUE);
                this.disclaimerPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                if (device.getConnectionType() == SIGPAD_CONNECTION_TYPE_USB) {
                    // in USB mode the image transfer is fast enough
                    // we can use the background buffer directly
                    this.signingPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                } else {
                    this.signingPageMemory = ImageMemory.requestPermanentStore(this.sigPad);
                }

            } else if (device.isModelDelta()) {
                this.sigPad.openDevice(this);
                controller.setImage(signatureImage, RegistrationConstants.RES_IMG_DEVICE_DELTA);

                dpi = 54f;
                displayPos = new Point(27, 66);
                maxPenWidth = PEN_WIDTH_DELTA_CONTROL;
                this.sigPad.setSampleRate(SampleRate.HZ_250);
                this.sigPad.setSignaturePenWidth(PEN_WIDTH_DELTA_PAD, Color.BLUE);
                this.disclaimerPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                if (device.getConnectionType() == SIGPAD_CONNECTION_TYPE_USB) {
                    // in USB mode the image transfer is fast enough
                    // we can use the background buffer directly
                    this.signingPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                } else {
                    this.signingPageMemory = ImageMemory.requestPermanentStore(this.sigPad);
                }

            } else if (device.isModelAlpha()) {
                this.sigPad.openDevice(this);
                controller.setImage(signatureImage, RegistrationConstants.RES_IMG_DEVICE_ALPHA);
                dpi = 26.9f;
                displayImg = RegistrationConstants.RES_IMG_LOGO_ALPHA;
                displayPos = new Point(149, 31);
                maxPenWidth = PEN_WIDTH_ALPHA_CONTROL;
                this.sigPad.setSignaturePenWidth(PEN_WIDTH_ALPHA_PAD, Color.BLUE);
                // this.disclaimerPageMemory is not used
                if (device.getConnectionType() == SIGPAD_CONNECTION_TYPE_USB) {
                    // in USB mode the image transfer is fast enough
                    // we can use the background buffer directly
                    this.signingPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                } else {
                    this.signingPageMemory = ImageMemory.requestPermanentStore(this.sigPad);
                }
            } else if (device.isModelPenDisplay()) {
                controller.setImage(signatureImage, RegistrationConstants.RES_IMG_DEVICE_DELTA);

                if (USE_CANVAS) {
                    final SignatureCanvas panel = new SignatureCanvas(this.sigPad,
                            device.getDisplayWidth(), device.getDisplayHeight());
                    panel.setMouseEnabled(true);
                    panel.setPenColor(Color.RED);
                    panel.setLocation(27, 66);
//                    panel.setStandbyImage(loadImage(RES_IMG_LOGO_PENDISPLAY));
//                    panel.setMaxPenWidth(PEN_WIDTH_PEN_DISPLAY_CONTROL_MAX);
//                    panel.setMinPenWidth(PEN_WIDTH_PEN_DISPLAY_CONTROL_MIN);

                    this.signatureComponent = panel;
                    this.signatureGraphics = panel.getSignatureGraphics();

                } else {
                    final SignatureJPanel panel = new SignatureJPanel(this.sigPad,
                            device.getDisplayWidth(), device.getDisplayHeight());
                    panel.setMouseEnabled(true);
                    panel.setPenColor(Color.BLUE);
                    panel.setLocation(27, 66);
                    panel.setStandbyImage(loadImage(controller.getImagePath(RegistrationConstants.RES_IMG_LOGO_PENDISPLAY, false)));
                    panel.setMaxPenWidth(PEN_WIDTH_PEN_DISPLAY_CONTROL_MAX);
                    panel.setMinPenWidth(PEN_WIDTH_PEN_DISPLAY_CONTROL_MIN);

                    this.signatureComponent = panel;
                    this.signatureGraphics = panel.getSignatureGraphics();
                }

                this.sigPad.openDevice(this.signatureComponent);
                this.sigPad.setSampleRate(SampleRate.HZ_250);
                this.sigPad.setSignaturePenWidth(3, Color.BLUE);
                this.sigPad.addSigPadListener(
                        new SigPadSwingAdapter((SigPadListener) this.signatureComponent));

                this.disclaimerPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
                this.signingPageMemory = ImageMemory.requestBackgroundBuffer(this.sigPad);
            } else {
                this.sigPad.closeDevice();
                LOGGER.info("This model type is not supported by this sample application!");
                return;
            }
        } catch (Exception e) {
            LOGGER.error("openPad - openDevice {}", e.getMessage());
            closePad();

            return;
        }

        if (device.isModelPenDisplay()) {
            // empty - the widget is already created
        } else if (USE_CANVAS) {
            // get CanvasBased Control from SigPadApi
            final SignatureCanvas canvas = new SignatureCanvas(this.sigPad, dpi);
            canvas.setMouseEnabled(true);
            canvas.setPenColor(Color.RED);
            canvas.setLocation(displayPos);
            canvas.setMaxPenWidth(maxPenWidth);

            this.sigPad.addSigPadListener(new SigPadSwingAdapter(canvas));
        } else {
            // get JPanelBased Control from SigPadApi
            final SignatureJPanel panel = new SignatureJPanel(this.sigPad, dpi);
            panel.setMouseEnabled(true);
            panel.setPenColor(Color.BLUE);
            panel.setLocation(displayPos);
            panel.setMaxPenWidth(maxPenWidth);
            panel.setBackground(Color.WHITE);

            if (displayImg != null) {
                panel.setStandbyImage(loadImage(displayImg));
            }
            this.signatureComponent = panel;
            this.signatureGraphics = panel.getSignatureGraphics();
            swingNode = new SwingNode();
            createSwingNode(swingNode, panel);
            controller.getGroupStackPane().getChildren().add(swingNode);
            this.sigPad.addSigPadListener(new SigPadSwingAdapter(panel));
        }

        if (this.signatureGraphics != null) {
            this.signatureGraphics.setBackgroundColor(Color.WHITE);
            this.signatureGraphics.setBorderColor(new Color(0xF07901));
            this.signatureGraphics.showStandbyImage();
        }
    }

    public void startPad() {
        switch (this.sigPad.getPad().getModelType()) {

            case SIGPAD_MODELTYPE_SIGMA_HID:
            case SIGPAD_MODELTYPE_SIGMA_SERIAL:
            case SIGPAD_MODELTYPE_ZETA_HID:
            case SIGPAD_MODELTYPE_ZETA_SERIAL:
            case SIGPAD_MODELTYPE_OMEGA_HID:
            case SIGPAD_MODELTYPE_OMEGA_SERIAL:
            case SIGPAD_MODELTYPE_GAMMA_HID:
            case SIGPAD_MODELTYPE_GAMMA_SERIAL:
            case SIGPAD_MODELTYPE_DELTA_HID:
            case SIGPAD_MODELTYPE_DELTA_SERIAL:
            case SIGPAD_MODELTYPE_DELTA_ETHERNET:
            case SIGPAD_MODELTYPE_PEN_DISPLAY:
            case SIGPAD_MODELTYPE_ALPHA_HID:
            case SIGPAD_MODELTYPE_ALPHA_SERIAL:
            case SIGPAD_MODELTYPE_ALPHA_ETHERNET:
                // Alpha
                startSignature();
                break;

            default:
                LOGGER.info("This model type is not supported by this sample application!");
        }
    }

    /**
     * Clears all data on the display.
     */
    private void clearDisplay() {
        try {
            this.sigPad.eraseDisplay();
        } catch (SigPadException e) {
            LOGGER.info( "{}\nclearDisplay - eraseDisplay", e.getMessage());
        }
    }

    /**
     * Start the sign procedure.
     */
    private void startSignature() {
        final SigPadDevice device = this.sigPad.getPad();
        SigPadRectangle[] hotspots = null;
        SigPadRectangle signingArea = null;

        try {
            // reset pad display
            clearDisplay();
            // reset window display - removes the standby image
            this.signatureGraphics.clearScreen();
            this.sigPad.clearSignRect();
            // remove hotspots
            this.sigPad.clearHotSpots();

            if (device.isModelSigma()) {
                // place background image (including buttons)
                this.sigPad.setImage(0, 0, this.imgSignature, this.signingPageMemory);
                // place the hotspots for the buttons
                hotspots = new SigPadRectangle[] {
                        new SigPadRectangle(10, 7, 89, 37, this.sigPad),
                        new SigPadRectangle(115, 7, 89, 37, this.sigPad),
                        new SigPadRectangle(220, 7, 89, 37, this.sigPad)
                };
                // signature area is the whole display except the toolbar
                signingArea = new SigPadRectangle(10, 54, 300, 96, this.sigPad);
            } else if (device.isModelZeta()) {
                // place background image (including buttons)
                this.sigPad.setImage(0, 0, this.imgSignature, this.signingPageMemory);
                // place the hotspots for the buttons
                hotspots = new SigPadRectangle[] {
                        new SigPadRectangle(10, 7, 89, 37, this.sigPad),
                        new SigPadRectangle(115, 7, 89, 37, this.sigPad),
                        new SigPadRectangle(220, 7, 89, 37, this.sigPad)
                };
                // signature area is the whole display except the toolbar
                signingArea = new SigPadRectangle(10, 54, 300, 136, this.sigPad);
            } else if (device.isModelOmega()) {
                // place background image
                this.sigPad.setImage(25, 100, this.imgSignature, this.signingPageMemory);
                // place the images for the buttons
//                this.sigPad.setImage(35, 9, this.imgCancel, this.signingPageMemory);
//                this.sigPad.setImage(235, 9, this.imgRetry, this.signingPageMemory);
//                this.sigPad.setImage(435, 9, this.imgOk, this.signingPageMemory);
                // specify the hotspots for the buttons
                hotspots = new SigPadRectangle[] {
                        new SigPadRectangle(33, 7, 174, 70, this.sigPad),
                        new SigPadRectangle(233, 7, 174, 70, this.sigPad),
                        new SigPadRectangle(433, 7, 174, 70, this.sigPad)
                };
                // signature area is the whole display except the toolbar
                signingArea = new SigPadRectangle(10, 87, 620, 383, this.sigPad);
            } else if (device.isModelGamma()) {
                // place background image (without buttons)
                this.sigPad.setImage(25, 140, this.imgSignature, this.signingPageMemory);
                // place the images for the buttons
//                this.sigPad.setImage(50, 20, this.imgCancel, this.signingPageMemory);
//                this.sigPad.setImage(312, 20, this.imgRetry, this.signingPageMemory);
//                this.sigPad.setImage(575, 20, this.imgOk, this.signingPageMemory);
                // specify the hotspots for the buttons
                hotspots = new SigPadRectangle[] {
                        new SigPadRectangle(48, 18, 174, 70, this.sigPad),
                        new SigPadRectangle(310, 18, 174, 70, this.sigPad),
                        new SigPadRectangle(573, 18, 174, 70, this.sigPad)
                };
                // signature area is the whole display except the toolbar
                signingArea = new SigPadRectangle(10, 98, 780, 372, this.sigPad);
            } else if (device.isModelDelta()) {
                // place background image (including buttons)
                this.sigPad.setImage(0, 0, this.imgSignature, this.signingPageMemory);
                // specify the hotspots for the buttons
                hotspots = new SigPadRectangle[] {
                        new SigPadRectangle(148, 16, 174, 70, this.sigPad),
                        new SigPadRectangle(553, 16, 174, 70, this.sigPad),
                        new SigPadRectangle(958, 16, 174, 70, this.sigPad)
                };
                // signature area is the whole display except the toolbar
                signingArea = new SigPadRectangle(10, 98, 1260, 692, this.sigPad);
            } else if (device.isModelAlpha()) {
                // show disclaimer text in a rectangle
                // place background image (without buttons)
                this.sigPad.setImage(89, 550, this.imgSignature, this.signingPageMemory);
                // place the images for the buttons
//                this.sigPad.setImage(107, 9, this.imgCancel, this.signingPageMemory);
//                this.sigPad.setImage(299, 9, this.imgRetry, this.signingPageMemory);
//                this.sigPad.setImage(491, 9, this.imgOk, this.signingPageMemory);
                // specify the hotspots for the buttons
                hotspots = new SigPadRectangle[] {
                        new SigPadRectangle(105, 7, 174, 70, this.sigPad),
                        new SigPadRectangle(297, 7, 174, 70, this.sigPad),
                        new SigPadRectangle(489, 7, 174, 70, this.sigPad)
                };
                // signature area is the whole display except the toolbar
                signingArea = new SigPadRectangle(10, 87, 748, 1269, this.sigPad);
            } else if (device.isModelPenDisplay()) {
                // place background image on the display
                this.sigPad.setImage(10, 80, this.imgSignature, this.signingPageMemory);
                // place buttons on the display
//                this.sigPad.setImage(25, 15, this.imgCancel, this.signingPageMemory);
//                this.sigPad.setImage(187, 15, this.imgRetry, this.signingPageMemory);
//                this.sigPad.setImage(351, 15, this.imgOk, this.signingPageMemory);
                // calculate hotspots
                hotspots = new SigPadRectangle[] {
                        new SigPadRectangle(23, 13, 89, 37, this.sigPad),
                        new SigPadRectangle(185, 13, 89, 37, this.sigPad),
                        new SigPadRectangle(349, 13, 89, 37, this.sigPad)
                };
                // signature area is the whole display except the toolbar
                final int areaWidth = device.getDisplayWidth() - 10;
                final int areaHeight = device.getDisplayHeight() - 60;
                signingArea = new SigPadRectangle(5, 55, areaWidth, areaHeight, this.sigPad);
            } else {
                LOGGER.info("This model type is not supported by this sample application!");
                return;
            }
            // draw the page on display
            this.sigPad.setImageFromStore(this.signingPageMemory);
        } catch (SigPadException e) {
            LOGGER.info("startSignature {}", e.getMessage() );
        }

        // add hotspot for cancel, retry, confirm
        try {
            if (hotspots != null) {
                this.sigPad.addHotSpot(hotspots[0]);
                this.sigPad.addHotSpot(hotspots[1]);
                this.sigPad.addHotSpot(hotspots[2]);
            }
        } catch (SigPadException e) {
            LOGGER.info(e.getMessage() + "\nstartSignature - addHotSpot", e);
        }
        this.sigPad.setHotSpotEventHandler(new SigningHotspotListener());

        // set signature area
        try {
            this.sigPad.setSignRect(signingArea);
        } catch (SigPadException e) {
            LOGGER.info(e.getMessage() + "\nstartSignature - setSignRect", e);
        }

        // start signature
        try {
            this.sigPad.startSignature();
        } catch (SigPadException e) {
            LOGGER.info(e.getMessage() + "\nstartSignature - startSignature", e);
        }

    }

    /**
     * @return Returns the manager for the connected signature pad. Returns <code>null</code> if no
     *         pad is connected.
     */
    private SigPadApi getSigPad() {
        return this.sigPad;
    }

    /**
     * Resets the signature for retry.
     */
    private void retrySignature() {
        try {
            this.sigPad.retrySignature();
        } catch (SigPadException e) {
            LOGGER.info(e.getMessage() + "\nretrySignature - retrySignature", e);
        }
    }

    private void confirmSignature() {
        try {
            this.sigPad.confirmSignature();

            // check if data was captured
            if (this.sigPad.getSignatureCount() > 0) {
                LOGGER.info("{} points succesfully captured.",this.sigPad.getSignatureCount());
                // enable save button and show number of captured signature points
                controller.getSaveBtn().setDisable(false);
                controller.getCancelBtn().setDisable(false);
            } else {
                controller.setScanningMsg("No valid signature");
            }
        } catch (SigPadException e) {
            LOGGER.info(e.getMessage() + "\nconfirmSignature - confirmSignature", e);
        }
    }

    public void closePadWindow(){
        this.closePad();
    }

    public BufferedImage saveSignature(){

        if (this.sigPad.getSignatureCount() == 0) {
            controller.setScanningMsg("No valid signature");
            return null;
        }

        try {
            final ImageType format = ImageType.PNG;
            Date date = new Date();
            final String fileDate = new SimpleDateFormat("yy-MM-dd").format(date) + String.valueOf(date.getTime());
            final String fileSuffix = '.' + format.name().toLowerCase();
            final String fileModel = this.sigPad.getPad().getModelName().replace(' ', '_');
            final String fileName = "Signature_" + fileModel + "_" + fileDate + fileSuffix;
            final String directory_path = "signatures";
            File directory = new File(directory_path);
            if(!directory.exists()){
                if(!directory.mkdirs()){
                    LOGGER.info("failed to create directory {}", directory_path);
                }
            }
            File signatureFile = new File(directory.getPath(),fileName);
            saveSignatureAsImage(signatureFile.getPath(),format);
            return ImageIO.read(signatureFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves the captured Signature as image to disk.
     *
     * @param fileName
     *            The name of the target file.
     * @param format
     *            The format of the encoded image.
     *
     *
     */
    private void saveSignatureAsImage(String fileName, ImageType format) {
        try {
            final int dpi = 150;
            final int penWidth = 0;
            final boolean addTimestamp = false;

            this.sigPad.setFont(new Font(FONT_NAME_PAD, Font.PLAIN, 40));
            this.sigPad.saveSignatureAsFile(dpi, penWidth, addTimestamp, format, fileName);
        } catch (IOException e) {
            LOGGER.info("saveSignature - {} - saveSignatureAsImage {}", fileName, e.getMessage());
        }

        finishSignature();
    }

    /**
     * The listener for the hotspots on the signing screen.
     */
    private final class SigningHotspotListener implements HotspotListener {

        private static final int HOTSPOT_ID_CANCEL = 0;
        private static final int HOTSPOT_ID_RETRY = 1;
        private static final int HOTSPOT_ID_CONFIRM = 2;

        @Override
        public void pressHotSpot(final int hotSpotId, boolean isPressed) {
            if (!isPressed) { // release of the button
                handleHotSpot(hotSpotId);
            }
        }

        private void handleHotSpot(int hotSpotId) {
            switch (hotSpotId) {

                case HOTSPOT_ID_CANCEL:
                    // we leave the page - ignore further hotspot changes
                    getSigPad().setHotSpotEventHandler(null);
                    cancelSignature();
                    break;

                case HOTSPOT_ID_RETRY:
                    retrySignature();
                    break;

                case HOTSPOT_ID_CONFIRM:
                    // we leave the page - ignore further hotspot changes
                    getSigPad().setHotSpotEventHandler(null);
                    confirmSignature();
                    break;

                default:
                    // do nothing
            }
        }

        @Override
        public void handleError(SigPadException cause) {
            SignaturePadService.this.handleError(cause);
        }
    }

    public static BufferedImage loadImage(String name) {
        try (InputStream is = SignaturePadService.class.getResourceAsStream(name)) {
            if (is != null) {
                return ImageIO.read(is);
            }
            return ImageIO.read(new File(name));
        } catch (IOException e) {
            LOGGER.info("error loading image: {} ", name);
        }

        return null;
    }

    private void createSwingNode(SwingNode node, JPanel jPanel){
        SwingUtilities.invokeLater(()->{
            node.setContent(jPanel);
        });
    }
}
