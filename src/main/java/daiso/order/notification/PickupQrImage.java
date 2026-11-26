package daiso.order.notification;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class PickupQrImage {
	//픽업토큰을 QR이미지로 만들어 png 바이트로반환한다.
	public static byte[]toPng(String pickupToken)
			throws WriterException,IOException{
		BitMatrix matrix = new QRCodeWriter()
				//encode가 토큰을 qr의 흑백격자로 바꿈
				.encode(pickupToken, BarcodeFormat.QR_CODE, 320, 320);
		
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		MatrixToImageWriter.writeToStream(matrix, "PNG", output);
		return output.toByteArray();
		
	}

}
