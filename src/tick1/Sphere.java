package tick1;

public class Sphere extends SceneObject {

	// Sphere coefficients
	private final double SPHERE_KD = 0.8;
	private final double SPHERE_KS = 1.2;
	private final double SPHERE_ALPHA = 10;
	private final double SPHERE_REFLECTIVITY = 0.3;

	// The world-space position of the sphere
	protected Vector3 position;

	public Vector3 getPosition() {
		return position;
	}

	// The radius of the sphere in world units
	private double radius;

	public Sphere(Vector3 position, double radius, ColorRGB colour) {
		this.position = position;
		this.radius = radius;
		this.colour = colour;

		this.phong_kD = SPHERE_KD;
		this.phong_kS = SPHERE_KS;
		this.phong_alpha = SPHERE_ALPHA;
		this.reflectivity = SPHERE_REFLECTIVITY;
	}

	public Sphere(Vector3 position, double radius, ColorRGB colour, double kD, double kS, double alphaS, double reflectivity) {
		this.position = position;
		this.radius = radius;
		this.colour = colour;

		this.phong_kD = kD;
		this.phong_kS = kS;
		this.phong_alpha = alphaS;
		this.reflectivity = reflectivity;
	}

	/*
	 * Calculate intersection of the sphere with the ray. If the ray starts inside the sphere,
	 * intersection with the surface is also found.
	 */
	public RaycastHit intersectionWith(Ray ray) {

		// Get ray parameters
		Vector3 O = ray.getOrigin();
		Vector3 D = ray.getDirection();

		// Get sphere parameters
		Vector3 C = position;
		double r = radius;

		// Calculate quadratic coefficients
		double a = D.dot(D);
		double b = 2 * D.dot(O.subtract(C));
		double c = (O.subtract(C)).dot(O.subtract(C)) - Math.pow(r, 2);

		// Determine if ray and sphere intersect - if not return an empty RaycastHit
		double det = b * b - 4 * a * c;
		if (det < 0) {
			return new RaycastHit();
		}

		// If so, work out any point of intersection
		else {
			double dist_lo = (-b - Math.pow(det, 0.5)) / 2.0;
			double dist_hi = (-b + Math.pow(det, 0.5)) / 2.0;

			// If both points are behind the camera, return an empty RaycastHit
			if ((dist_lo <= 0) & (dist_hi <= 0)) {
				return new RaycastHit();
			}

			// If one is positive and one negative, return a RaycastHit for the positive solution
			else if ((dist_lo <= 0) & (dist_hi > 0)) {
				Vector3 location = ray.evaluateAt(dist_hi);
				Vector3 normal = this.getNormalAt(location);

				return new RaycastHit(this, dist_hi, location, normal);
			}

			// Otherwise return the solution nearer to the camera
			else {
				Vector3 location = ray.evaluateAt(dist_lo);
				Vector3 normal = this.getNormalAt(location);

				return new RaycastHit(this, dist_lo, location, normal);
			}
		}
	}

	// Get normal to surface at position
	public Vector3 getNormalAt(Vector3 position) {
		return position.subtract(this.position).normalised();
	}
}
