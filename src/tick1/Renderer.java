package tick1;

import java.awt.image.BufferedImage;
import java.util.List;

public class Renderer {
	
	// The width and height of the image in pixels
	private int width, height;
	
	// Bias factor for reflected and shadow rays
	private final double EPSILON = 0.0001;

	// The number of times a ray can bounce for reflection
	private int bounces;
	
	// Background colour of the image
	private ColorRGB backgroundColor = new ColorRGB(0.001);

	// Number of shadow rays cast for soft shadows
	private final double SHADOW_RAY_COUNT = 20;

	// Size of each light source
	private final double LIGHT_SIZE = 0.4;

	// Distributed depth-of-field constants
	private final int DOF_RAY_COUNT = 150; // No. of spawned DoF rays
	private final double DOF_FOCAL_PLANE = 3.51805; // Focal length of camera
	private final double DOF_AMOUNT = 0.07; // Amount of DoF effect

	public Renderer(int width, int height, int bounces) {
		this.width = width;
		this.height = height;
		this.bounces = bounces;
	}

	/*
	 * Trace the ray through the supplied scene, returning the colour to be rendered.
	 * The bouncesLeft parameter is for rendering reflective surfaces.
	 */
	protected ColorRGB trace(Scene scene, Ray ray, int bouncesLeft) {

		// Find closest intersection of ray in the scene
		RaycastHit closestHit = scene.findClosestIntersection(ray);

        // If no object has been hit, return a background colour
        SceneObject object = closestHit.getObjectHit();
        if (object == null){
            return backgroundColor;
        }
        
        // Otherwise calculate colour at intersection and return
        // Get properties of surface at intersection - location, surface normal
        Vector3 P = closestHit.getLocation();
        Vector3 N = closestHit.getNormal();
        Vector3 O = ray.getOrigin();

		// Calculate direct illumination at the point
		ColorRGB directIllumination = this.illuminate(scene, object, P, N, O);

		// Get reflectivity of object
		double reflectivity = object.getReflectivity();

		// Base case - if no bounces left or non-reflective surface
		if (bouncesLeft == 0 || reflectivity == 0) {
			return directIllumination;
		}
		else { // Recursive case
			ColorRGB reflectedIllumination;

			// Calculate the direction R of the bounced ray
			Vector3 R = ray.getDirection().reflectIn(N).scale(-1);

			// Spawn a reflectedRay with bias
			Ray reflectedRay = new Ray(P.add(R.scale(EPSILON)), R);

			// Calculate reflectedIllumination by tracing reflectedRay
			reflectedIllumination = trace(scene, reflectedRay, bouncesLeft - 1);

			// Scale direct and reflective illumination to conserve light
			directIllumination = directIllumination.scale(1.0 - reflectivity);
			reflectedIllumination = reflectedIllumination.scale(reflectivity);

			return directIllumination.add(reflectedIllumination);
		}
	}

	/*
	 * Illuminate a surface on and object in the scene at a given position P and surface normal N,
	 * relative to ray originating at O
	 */
	private ColorRGB illuminate(Scene scene, SceneObject object, Vector3 P, Vector3 N, Vector3 O) {
	   
		ColorRGB colourToReturn = new ColorRGB(0);

		ColorRGB I_a = scene.getAmbientLighting(); // Ambient illumination intensity

		ColorRGB C_diff = object.getColour(); // Diffuse colour defined by the object
		
		// Get Phong coefficients
		double k_d = object.getPhong_kD();
		double k_s = object.getPhong_kS();
		double alpha = object.getPhong_alpha();

		// Add ambient light term
		colourToReturn = colourToReturn.add(C_diff.scale(I_a));

		// Loop over each point light source
		List<PointLight> pointLights = scene.getPointLights();
		for (int i = 0; i < pointLights.size(); i++) {
			PointLight light = pointLights.get(i); // Select point light

			// Calculate point light constants
			double distanceToLight = (light.getPosition().subtract(P)).magnitude();
			ColorRGB C_spec = light.getColour();
			ColorRGB I = light.getIlluminationAt(distanceToLight);

			// Calculate L, V, R
			Vector3 L = (light.getPosition().subtract(P)).normalised();
			Vector3 V = (O.subtract(P)).normalised();
			Vector3 R = L.reflectIn(N).normalised();

			// Scale factor for soft shadows, which will be used to scale diffuse and specular components
			double shadowScaleFactor = 0;

			// Cast a number of shadow rays to points within the light, averaging the contributions of each
			for (int j = 0; j < SHADOW_RAY_COUNT; j++) {
				// Select a random point within the light source
				Vector3 locationInLight = Vector3.randomInsideUnitSphere().scale(LIGHT_SIZE);

				// Cast a ray to that random point, and get its intersection with scene objects
				Vector3 shadowDirection = (light.getPosition().add(locationInLight).subtract(P)).normalised();
				Ray shadowRay = new Ray(P.add(shadowDirection.scale(EPSILON)), shadowDirection);
				RaycastHit shadowInt = scene.findClosestIntersection(shadowRay);

				// If the ray is not obstructed, adjust the scale factor
				if (shadowInt.getDistance() > distanceToLight) {
					shadowScaleFactor += 1 / SHADOW_RAY_COUNT;
				}
			}

			// Add diffuse/specular components
			if (N.dot(L) > 0) {
				ColorRGB diffuse = I.scale(C_diff.scale(k_d * N.dot(L) * shadowScaleFactor));
				colourToReturn = colourToReturn.add(diffuse);
			}
			if (R.dot(V) > 0) {
				ColorRGB specular = I.scale(C_spec.scale(k_s * Math.pow(R.dot(V), alpha) * shadowScaleFactor));
				colourToReturn = colourToReturn.add(specular);
			}
		}
		return colourToReturn;
	}

	// Render image from scene, with camera at origin
	public BufferedImage render(Scene scene) {
		
		// Set up image
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		// Set up camera
		Camera camera = new Camera(width, height);

		// Loop over all pixels
		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				Ray ray = camera.castRay(x, y); // Cast ray through pixel

				// Find ray intersection with focal plane
				Vector3 intersectionPoint = ray.getDirection().scale(DOF_FOCAL_PLANE / ray.getDirection().z);

				// Initialise RGB value for the pixel
				ColorRGB linearRGB = new ColorRGB(0);

				// Cast rays randomly from the camera aperture through the intersection point
				for (int i = 0; i < DOF_RAY_COUNT; i++){
					Vector3 origin = new Vector3((-1 + 2 * Math.random()) * DOF_AMOUNT, (-1 + 2 * Math.random()) * DOF_AMOUNT, 0);
					Vector3 rayDirection = (intersectionPoint.subtract(origin)).normalised();
					Ray dof_Ray = new Ray(origin, rayDirection);

					// Add a scaled contribution from tracing the ray
					ColorRGB colourToAdd = trace(scene, dof_Ray, bounces).scale(1 / (float) DOF_RAY_COUNT);
					linearRGB = linearRGB.add(colourToAdd);
				}

				ColorRGB gammaRGB = tonemap( linearRGB );
				image.setRGB(x, y, gammaRGB.toRGB()); // Set image colour to traced colour
			}
			// Display progress every 10 lines
            if( y % 10 == 0 )
			    System.out.println(String.format("%.2f", 100 * y / (float) (height - 1)) + "% completed");
		}
		return image;
	}


	// Combined tone mapping and display encoding
	public ColorRGB tonemap( ColorRGB linearRGB ) {
		double invGamma = 1./2.2;
		double a = 2;  // controls brightness
		double b = 1.3; // controls contrast

		// Sigmoidal tone mapping
		ColorRGB powRGB = linearRGB.power(b);
		ColorRGB displayRGB = powRGB.scale( powRGB.add(Math.pow(0.5/a,b)).inv() );

		// Display encoding - gamma
		ColorRGB gammaRGB = displayRGB.power( invGamma );

		return gammaRGB;
	}


}
