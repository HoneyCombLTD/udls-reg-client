package io.mosip.registration.util.control.impl;

import com.sun.activation.viewers.TextViewer;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.dto.mastersync.GenericDto;
import io.mosip.registration.dto.schema.UiFieldDTO;
import io.mosip.registration.util.control.FxControl;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PRNStatusInputFxControl extends TextFieldFxControl {

    @Autowired
    BaseController baseController;

    private boolean PRN_OK = false;
    private String PRN;

    @Override
    public FxControl build(UiFieldDTO uiFieldDTO) {
        this.uiFieldDTO = uiFieldDTO;
        this.control = this;

        GridPane prnVerificationGridPane = new GridPane();
        RowConstraints labelConstraint = new RowConstraints();
        RowConstraints inputConstraint = new RowConstraints();
        RowConstraints previewConstraint = new RowConstraints();

        labelConstraint.setPercentHeight(10);
        inputConstraint.setPercentHeight(20);
        previewConstraint.setPercentHeight(60);

        prnVerificationGridPane.getRowConstraints().addAll(labelConstraint, inputConstraint, previewConstraint);
        prnVerificationGridPane.setPrefHeight(150);

        prnVerificationGridPane.add(createVerifyButton(uiFieldDTO), 1,1);
        prnVerificationGridPane.add(create(uiFieldDTO), 0, 0);
        prnVerificationGridPane.add(createPreview(uiFieldDTO), 0, 2);
        this.node = prnVerificationGridPane;
        return this.control;
    }

    @Override
    public void setListener(Node node) {
        if(node instanceof Button){
            Button button = (Button) node;
            button.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    String langCode = getRegistrationDTo().getSelectedLanguagesByApplicant().get(0);
                    TextField prn_input = (TextField) getField(uiFieldDTO.getId()+langCode);
                    PRN = prn_input.getText();
                    try {
                        String url = "https://uraproxy.techno-associates.live/ura/get-prn-status?PRN="+PRN;
                        OkHttpClient client = new OkHttpClient();
                        Request req = new Request.Builder()
                                .url(url)
                                .addHeader("User-Agent", "Mozilla/5.0")
                                .build();
                        try(Response response =  client.newCall(req).execute()){
                            if(response.isSuccessful()){
                                assert response.body() != null;
                                String resp_str = response.body().string();
                                System.out.println("Response: "+resp_str);
                                JSONObject resp = new JSONObject(resp_str);
                                JSONObject statusResult = resp.getJSONObject("checkPRNStatusResult");
                                String error = statusResult.getString("errorDesc");
                                String status = statusResult.getString("statusCode")!=null?statusResult.getString("statusCode"):null;
                                String prn_const;
                                if(!error.isEmpty() && status==null){
                                    prn_const = error;
                                }else {
                                    prn_const = (new StringBuilder()).append("\n PRN Status\n")
                                            .append("Status:              ").append(statusResult.getString("statusDesc")).append("\n")
                                            .append("Tax Payer Name:      ").append(statusResult.getString("taxPayerName")).append("\n")
                                            .append("Amount:              ").append(statusResult.getString("amountPaid")).append("\n")
//                                        .append("Payment Received On: ").append(statusResult.getString("realizationDate")!=null?statusResult.getString("realizationDate"):" ").append("\n")
//                                        .append("PRN Expiry Date:     ").append(statusResult.getString("paymentExpiryDate")).append("\n")
                                            .toString();
                                    PRN_OK = statusResult.getString("statusCode").equalsIgnoreCase("T");
                                }
                                ((Text)getField(uiFieldDTO.getId()+RegistrationConstants.PRN_PREVIEW)).setText(prn_const);

                            }else{
                                System.out.println("GET request failed with code "+response.code());
                            }
                        }
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                }
            });
        }else {
            super.setListener(node);
        }
    }

    @Override
    public void selectAndSet(Object data) {

    }

    @Override
    public boolean isValid() {
        return this.PRN_OK;
    }

    @Override
    public boolean isEmpty() {
        return PRN.isEmpty();
    }

    @Override
    public List<GenericDto> getPossibleValues(String langCode) {
        return List.of();
    }

    private Node createVerifyButton(UiFieldDTO uiFieldDTO){
        Button verifyPrnButton =  new Button();
        verifyPrnButton.setText(ApplicationContext.getBundle(null, RegistrationConstants.LABELS).getString(
                RegistrationConstants.VERIFY_PRN
        ));
        verifyPrnButton.setId(uiFieldDTO.getId() + RegistrationConstants.PRN_INPUT+"verify-btn");
        verifyPrnButton.getStyleClass().add(RegistrationConstants.DOCUMENT_CONTENT_BUTTON);
        setListener(verifyPrnButton);
        return verifyPrnButton;

    }

    private Node createPrnInputTextField(UiFieldDTO uiFieldDTO){
        HBox textFieldHBox = new HBox();
        TextField textField = new TextField();
        textField.setId(uiFieldDTO.getId()+RegistrationConstants.PRN_INPUT);
        textField.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
        textField.setMinWidth(300);
        textFieldHBox.getChildren().add(textField);
        return textFieldHBox;
    }

    private Node createPreview(UiFieldDTO uiFieldDTO){
        Text preview = new Text();
        preview.setId(uiFieldDTO.getId()+RegistrationConstants.PRN_PREVIEW);
        Pane previewPane = new Pane();
        previewPane.setMaxWidth(400);
        previewPane.getChildren().add(preview);
        return  previewPane;
    }

}
