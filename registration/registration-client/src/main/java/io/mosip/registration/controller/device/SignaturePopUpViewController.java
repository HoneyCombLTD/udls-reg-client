package io.mosip.registration.controller.device;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.api.docscanner.DocScannerFacade;
import io.mosip.registration.api.docscanner.dto.DocScanDevice;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.reg.DocumentScanController;
import io.mosip.registration.signaturepad.SignaturePadService;
import io.mosip.registration.util.common.RectangleSelection;
import io.mosip.registration.util.control.FxControl;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ResourceBundle;

@Controller
public class SignaturePopUpViewController extends BaseController implements Initializable {
	private static final Logger LOGGER = AppConfig.getLogger(SignaturePopUpViewController.class);

	@FXML
	private Button saveBtn;
	@FXML
	private Text scanningMsg;
	/*@FXML
	private Hyperlink closeButton;*/
	@FXML
	protected Label docPreviewNext;
	@FXML
	protected Label docPreviewPrev;
	@FXML
	protected Text docCurrentPageNumber;
	@FXML
	protected GridPane previewOption;

	@FXML
	private Button cancelBtn;
	@FXML
	private Button stopButton;
	@FXML
	private Button streamBtn;



	@FXML
	private GridPane imageViewGridPane;
	@FXML
	private ImageView signatureImage;
	/*@FXML
	private ImageView closeImageView;*/
	@FXML
	private ImageView streamImageView;
	@FXML
	private ImageView saveImageView;
	@FXML
	private ImageView backImageView1;
	@FXML
	private ImageView cancelImageView;	
	@FXML
	private ImageView previewImageView;

	@FXML
	private StackPane groupStackPane;

	@Autowired
	private BaseController baseController;
	@Autowired
	private Streamer streamer;
	@Autowired
	private DocumentScanController documentScanController;
	@Autowired
	private DocScannerFacade docScannerFacade;

	@Value("${mosip.doc.stage.width:1200}")
	private int width;

	@Value("${mosip.doc.stage.height:620}")
	private int height;

	private FxControl fxControl;
	private String fieldId;
	private Thread streamer_thread = null;
	private Stage popupStage;
	public TextField streamerValue;
	private boolean isWebCamStream;
	private boolean isStreamPaused;
	public DocScanDevice docScanDevice;
	private RectangleSelection rectangleSelection = null;
	final DoubleProperty zoomProperty = new SimpleDoubleProperty(200);

	private SignaturePadService signaturePadService;

	public StackPane getGroupStackPane() {
		return groupStackPane;
	}

	public boolean isStreamPaused() {
		return isStreamPaused;
	}

	public boolean isWebCamStream() {
		return isWebCamStream;
	}

	public void setWebCamStream(boolean isWebCamStream) {
		this.isWebCamStream = isWebCamStream;
	}

	/**
	 * @return the popupStage
	 */
	public Stage getPopupStage() {
		return popupStage;
	}

	/**
	 * @return the scanImage
	 */
	public ImageView getSignatureImage() {
		return signatureImage;
	}

	/**
	 * @return save button
	 */
	public Button getSaveBtn(){
		return saveBtn;
	}

	public void setSaveBtn(Button saveBtn) {
		this.saveBtn = saveBtn;
	}

	public Button getCancelBtn() {
		return cancelBtn;
	}

	public void setCancelBtn(Button cancelBtn) {
		this.cancelBtn = cancelBtn;
	}

	/**
	 * @param popupStage the popupStage to set
	 */
	public void setPopupStage(Stage popupStage) {
		this.popupStage = popupStage;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		
		//setImage(closeImageView	, RegistrationConstants.CLOSE_IMG);
		setImage(streamImageView	, RegistrationConstants.STREAM_IMG);
		setImage(saveImageView	, RegistrationConstants.DWLD_PRE_REG_DATA_IMG);
		setImage(backImageView1	, RegistrationConstants.CROP_IMG);
		setImage(cancelImageView	, RegistrationConstants.REJECT_IMG);
		setImage(previewImageView	, RegistrationConstants.HOVER_IMG);
	}


	public void captureSignature(String fieldId, FxControl control){
		this.fxControl = control;
		this.fieldId = fieldId;
		initializeAndShowSignPopUp();
	}

	/**
	 * This method will open popup to scan
	 * 
	 * @param parentControllerObj
	 * @param title
	 */
	public void init(BaseController parentControllerObj, String title) {

			streamerValue = new TextField();
			baseController = parentControllerObj;

			initializeAndShowSignPopUp();
	}

	private void initializeAndShowSignPopUp(){
		try{

			signaturePadService = SignaturePadService.getInstance();

			LOGGER.info("Loading signing page : {}", RegistrationConstants.SIGN_PAGE);
			Parent scanPopup = BaseController.load(getClass().getResource(RegistrationConstants.SIGN_PAGE));
			signatureImage.setPreserveRatio(true);
			stopButton.setDisable(true);
			cancelBtn.setDisable(true);
			previewOption.setVisible(false);


			LOGGER.info("Setting sign screen width :{}, height: {}", width, height);
			Scene scene = new Scene(scanPopup, width, height);
			saveBtn.setDisable(true);
			stopButton.setDisable(true);
			cancelBtn.setDisable(true);
			scene.getStylesheets().add(ClassLoader.getSystemClassLoader().getResource(getCssName()).toExternalForm());
			popupStage = new Stage();
			popupStage.setAlwaysOnTop(false);
			popupStage.setScene(scene);
			popupStage.initModality(Modality.WINDOW_MODAL);
			popupStage.initOwner(fXComponents.getStage());
			popupStage.setTitle("Capture Signature");
			popupStage.setMinHeight(height);
			popupStage.setMinWidth(width);
			popupStage.show();

			LOGGER.debug("signature screen launched");
			scanningMsg.textProperty().addListener((observable, oldValue, newValue) -> {
				Platform.runLater(() -> {
					if (RegistrationUIConstants.NO_DEVICE_FOUND.contains(newValue)) {
						generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.NO_DEVICE_FOUND);
						popupStage.close();
					}
				});
			});

			rectangleSelection = null;
			clearSelection();

			LOGGER.info("Opening pop-up screen to sign for user registration");

		}catch (IOException exception){
			LOGGER.error(RegistrationConstants.USER_REG_SCAN_EXP, exception);
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.UNABLE_LOAD_SCAN_POPUP));
		}
	}

	@FXML
	public void preview() {
	}


	@FXML
	public void stream() {

	}

	@FXML
	public void startDevice(){
		LOGGER.info("......... Start Device ....... ");
		if(!signaturePadService.getDevices()){
			scanningMsg.setText(RegistrationUIConstants.NO_DEVICE_FOUND);
			LOGGER.info("...... no device found .....");
			return;
		}
		signaturePadService.openPad(this, this.signatureImage);
		signaturePadService.startPad();
		streamBtn.setDisable(true);
		LOGGER.info("......... Device start finish .....");
	}

	/**
	 * This method will allow to scan
	 *
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	@FXML
	public void scan() throws MalformedURLException, IOException {
	}

	@FXML
	private void save() {
		// Enable Auto-Logout
		SessionContext.setAutoLogout(true);
		try {
			fxControl.setData(signaturePadService.saveSignature());
			popupStage.close();

		} catch (RuntimeException exception) {
			LOGGER.error("Failed to set data in documentDTO", exception);
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SAVE_SIGNATURE_ERROR));
		}
	}

	@FXML
	public void stopDevice() {

	}


	@FXML
	public void cancel() {
		saveBtn.setDisable(true);
		streamBtn.setDisable(false);
	}

	private void stopStreaming() {
		try {
			setWebCamStream(false);
			isStreamPaused = true;
			if(streamer_thread != null)
				streamer_thread.interrupt();
		} finally {
			docScannerFacade.stopDevice(this.docScanDevice);
		}
	}


	/**
	 * event class to exit from present pop up window.
	 *
	 * @param event
	 */
	public void exitWindow(ActionEvent event) {
		LOGGER.info("Calling exit window to close the popup");
		popupStage = (Stage) ((Node) event.getSource()).getParent().getScene().getWindow();
		popupStage.close();
		signaturePadService.closePadWindow();
		LOGGER.info("Sign Popup is closed");

	}


	public Text getScanningMsg() {
		return scanningMsg;
	}

	public void setScanningMsg(String msg) {
		if (scanningMsg != null) {
			scanningMsg.setText(msg);
			scanningMsg.getStyleClass().add("scanButton");
		}
	}


	public void save(Bounds bounds, BufferedImage bufferedImage) {

	}

	private void clearSelection() {
	}

}
