package io.mosip.registration.util.control.impl;

import io.mosip.registration.api.docscanner.DocScannerUtil;
import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.controller.ClientApplication;
import io.mosip.registration.controller.device.SignaturePopUpViewController;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.registration.dto.packetmanager.DocumentDto;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.util.control.FxControl;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SignatureFxControl extends FxControl {

    @Autowired
    private SignaturePopUpViewController signaturePopUpViewController;

    public SignatureFxControl() {
        org.springframework.context.ApplicationContext applicationContext = ClientApplication.getApplicationContext();
        signaturePopUpViewController = applicationContext.getBean(SignaturePopUpViewController.class);
        auditFactory = applicationContext.getBean(AuditManagerService.class);
    }

    @Override
    public FxControl build(UiFieldDTO uiFieldDTO) {
        this.uiFieldDTO = uiFieldDTO;
        this.control = this;

        GridPane signatureGridPane = new GridPane();
        RowConstraints labelConstraint = new RowConstraints();
        RowConstraints buttonConstraint = new RowConstraints();
        RowConstraints previewConstraint = new RowConstraints();

        labelConstraint.setPercentHeight(20);
        buttonConstraint.setPercentHeight(20);
        previewConstraint.setPercentHeight(60);
        signatureGridPane.getRowConstraints().addAll(labelConstraint, buttonConstraint, previewConstraint);
        signatureGridPane.setPrefWidth(200);

        signatureGridPane.add(create(uiFieldDTO), 0, 0);
        signatureGridPane.add(createSignButton(uiFieldDTO), 0, 1);
        signatureGridPane.add(createPreview(uiFieldDTO), 0, 2);
        signatureGridPane.add(createClearSignatureButton(uiFieldDTO), 1, 2);

        this.node = signatureGridPane;
        setListener(getField(uiFieldDTO.getId() + RegistrationConstants.SIGNATURE));
        return this.control;
    }

    @Override
    public void setListener(Node node) {
        Button signButton = (Button) node;
        signButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                showSignaturePopUp();
                signButton.setDisable(true);
            }
        });
    }

    /**
     * show pop up screen for signing
     */
    private void showSignaturePopUp() {
        signaturePopUpViewController.captureSignature(uiFieldDTO.getId(), this);
    }

    @Override
    public void setData(Object data) {

        if (data != null) {
            // set preview
            ImageView preview = (ImageView) getField(uiFieldDTO.getId() + RegistrationConstants.IMAGE_VIEW);
            preview.setImage(DocScannerUtil.getImage((BufferedImage) data));
            Button clear = (Button) getField(uiFieldDTO.getId() + RegistrationConstants.CLEAR);
            clear.setVisible(true);
        } else {
            signaturePopUpViewController.generateAlert(RegistrationConstants.ERROR,
                    "Unable to read signature image");
            return;
        }

        try {
            // store data as image
            List<BufferedImage> bufferedImages = new ArrayList<BufferedImage>();
            bufferedImages.add((BufferedImage) data);
            byte[] byteArray = DocScannerUtil.asImage(bufferedImages);
            if (byteArray == null) {
                signaturePopUpViewController.generateAlert(RegistrationConstants.ERROR, "Signature Storage Error");
                return;
            }
            int docSize = (int) Math.ceil(
                    Integer.parseInt(signaturePopUpViewController.getValueFromApplicationContext(
                            RegistrationConstants.DOC_SIZE)) / (double) (1024 * 1024));
            if (docSize <= (byteArray.length / (1024 * 1024))) {
                bufferedImages.clear();
                signaturePopUpViewController.generateAlert(RegistrationConstants.ERROR,
                        RegistrationUIConstants.getMessageLanguageSpecific(RegistrationUIConstants.SCAN_DOC_SIZE)
                                .replace("1", Integer.toString(docSize)));
                return;
            }
            String signatureString = Base64.getEncoder().encodeToString(byteArray);
            if (signatureString != null) {
                signatureString = "data:image/png;base64," + signatureString;
            }
            getRegistrationDTo().addDemographicField(uiFieldDTO.getId(), signatureString);
            // DocumentDto documentDto =
            // getRegistrationDTo().getDocuments().get(uiFieldDTO.getId());
            // if(documentDto == null){
            // documentDto = new DocumentDto();
            // documentDto.setFormat("png");
            // documentDto.setCategory(uiFieldDTO.getSubType());
            // documentDto.setOwner(RegistrationConstants.APPLICANT);
            // }
            // String type = "signature";
            // documentDto.setType(type);
            // documentDto.setValue(uiFieldDTO.getSubType().concat(RegistrationConstants.UNDER_SCORE).concat(type));
            // documentDto.setDocument(byteArray);
            // documentDto.setRefNumber("resident signature");
            // getRegistrationDTo().addDocument(uiFieldDTO.getId(), documentDto);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void clearData() {
        getRegistrationDTo().getDemographics().remove(uiFieldDTO.getId());
    }

    @Override
    public void fillData(Object data) {

    }

    @Override
    public Object getData() {
        return getRegistrationDTo().getDemographics().get(uiFieldDTO.getId());
    }

    @Override
    public void selectAndSet(Object data) {

    }

    @Override
    public boolean isValid() {
        return getData() != null;
    }

    @Override
    public boolean isEmpty() {
        return !isValid();
    }

    @Override
    public List<GenericDto> getPossibleValues(String langCode) {
        return List.of();
    }

    private VBox create(UiFieldDTO uiFieldDTO) {
        String fieldName = uiFieldDTO.getId();
        /** Container holds title, fields and validation message elements */
        VBox simpleTypeVBox = new VBox();
        simpleTypeVBox.setId(fieldName + RegistrationConstants.VBOX);
        simpleTypeVBox.setSpacing(5);

        double prefWidth = 300;

        List<String> labels = new ArrayList<>();
        getRegistrationDTo().getSelectedLanguagesByApplicant().forEach(lCode -> {
            labels.add(this.uiFieldDTO.getLabel().get(lCode));
        });
        String titleText = String.join(RegistrationConstants.SLASH, labels) + getMandatorySuffix(uiFieldDTO);

        /** Title label */
        Label fieldTitle = getLabel(fieldName + RegistrationConstants.LABEL, titleText,
                RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL, true, prefWidth);
        simpleTypeVBox.getChildren().add(fieldTitle);

        Label messageLabel = getLabel(uiFieldDTO.getId() + RegistrationConstants.MESSAGE, null,
                RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL, false, simpleTypeVBox.getPrefWidth());
        messageLabel.setWrapText(true);
        messageLabel.setPrefWidth(prefWidth);
        messageLabel.setManaged(false);
        simpleTypeVBox.getChildren().add(messageLabel);

        return simpleTypeVBox;
    }

    private Node createSignButton(UiFieldDTO uiFieldDTO) {
        Button signButton = new Button();
        signButton.setText(ApplicationContext.getBundle(null, RegistrationConstants.LABELS).getString(
                RegistrationConstants.SIGN));
        signButton.setId(uiFieldDTO.getId() + RegistrationConstants.SIGNATURE);
        signButton.getStyleClass().add(RegistrationConstants.DOCUMENT_CONTENT_BUTTON);
        signButton.setGraphic(new ImageView(
                new Image(
                        this.getClass().getResourceAsStream(RegistrationConstants.IMG_SIGN),
                        12, 12, true, true)));
        return signButton;

    }

    private Node createClearSignatureButton(UiFieldDTO uiFieldDTO) {
        Button clearButton = new Button();
        clearButton.setText(ApplicationContext.getBundle(null, RegistrationConstants.LABELS)
                .getString(RegistrationConstants.CLEAR));
        clearButton.setVisible(false);
        clearButton.setId(uiFieldDTO.getId() + RegistrationConstants.CLEAR);
        clearButton.setGraphic(new ImageView(
                new Image(
                        this.getClass().getResourceAsStream(RegistrationConstants.IMG_CLEAR_SIGNATURE),
                        12, 12, true, true)));

        clearButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                clearSignature();
                getField(uiFieldDTO.getId() + RegistrationConstants.CLEAR).setVisible(false);
                getField(uiFieldDTO.getId() + RegistrationConstants.SIGNATURE).setDisable(false);
            }
        });
        return clearButton;
    }

    private void clearSignature() {
        LOGGER.info("..... Clearing signature .....");
        ((ImageView) getField(uiFieldDTO.getId() + RegistrationConstants.IMAGE_VIEW)).setImage(null);
        clearData();
    }

    private Node createPreview(UiFieldDTO uiFieldDTO) {
        ImageView imageView = new ImageView();
        imageView.setId(uiFieldDTO.getId() + RegistrationConstants.IMAGE_VIEW);
        return imageView;
    }
}
