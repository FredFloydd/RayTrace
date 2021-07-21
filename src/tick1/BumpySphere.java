package tick1;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class BumpySphere extends Sphere {

	private float BUMP_FACTOR = 5f;
	private float[][] bumpMap;
	private int bumpMapHeight;
	private int bumpMapWidth;

	public BumpySphere(Vector3 position, double radius, ColorRGB colour, String bumpMapImg) {
		super(position, radius, colour);
		try {
			BufferedImage inputImg = ImageIO.read(new File(bumpMapImg));
			bumpMapHeight = inputImg.getHeight();
			bumpMapWidth = inputImg.getWidth();
			bumpMap = new float[bumpMapHeight][bumpMapWidth];
			for (int row = 0; row < bumpMapHeight; row++) {
				for (int col = 0; col < bumpMapWidth; col++) {
					float height = (float) (inputImg.getRGB(col, row) & 0xFF) / 0xFF;
					bumpMap[row][col] = BUMP_FACTOR * height;
				}
			}
		} catch (IOException e) {
			System.err.println("Error creating bump map");
			e.printStackTrace();
		}
	}

	// Get normal to surface at position
	@Override
	public Vector3 getNormalAt(Vector3 position) {

		// Start with the normal for a standard sphere
		Vector3 sphereNormal = position.subtract(this.position).normalised();

		// Define u and v, which are the polar and azimuthal angles
		double u = Math.acos(Math.abs(sphereNormal.y));
		double v = Math.acos(sphereNormal.x / Math.sin(u));

		if (sphereNormal.y < 0){
			u *= -1;
		}
		if (sphereNormal.z < 0){
			v *= -1;
		}

		double sin_u = Math.sin(u);
		double cos_u = Math.cos(u);
		double sin_v = Math.sin(v);
		double cos_v = Math.cos(v);

		// Define the tangent vectors at the given point
		Vector3 P_u = new Vector3(sin_u * cos_v, sin_u * sin_v, cos_u);
		Vector3 P_v = new Vector3(-sin_u * sin_v, sin_u * cos_v, 0);

		// Find the corresponding point on the bump map
		int bumpMapUCoordinate = (int) Math.round(bumpMapHeight / 2 - u * bumpMapHeight / Math.PI);
		int bumpMapVCoordinate = (int) Math.round(bumpMapWidth / 2 + v * bumpMapWidth / (2 * Math.PI));

		// Adjust the returned normal based off the bump map value
		if (bumpMapUCoordinate < bumpMapHeight - 1 && bumpMapVCoordinate < bumpMapWidth - 1){
			float B_u = bumpMap[bumpMapUCoordinate][bumpMapVCoordinate] - bumpMap[bumpMapUCoordinate + 1][bumpMapVCoordinate];
			float B_v = bumpMap[bumpMapUCoordinate][bumpMapVCoordinate] - bumpMap[bumpMapUCoordinate][bumpMapVCoordinate + 1];
			return (sphereNormal.add(sphereNormal.cross(P_v).scale(B_v)).add(sphereNormal.cross(P_u).scale(B_u))).normalised();
		}
		else return sphereNormal;
	}
}
