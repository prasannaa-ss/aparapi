package aparapi.examples.raytrace;

import com.amd.aparapi.Kernel;

public class Raytracer extends Kernel {

	protected final int screenWidth;
	protected final int screenHeight;
	private final int MaxDepth = 10;
	private final Scene scene;
	private final int[] image;

    
	public Raytracer(int screenWidth, int screenHeight, int[] image) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		scene = DefaultScene();
		this.image = image;
	}

	private ISect Intersections(Ray ray, Scene scene) {
		double min_dist = Double.MAX_VALUE;
		ISect minimum = null;
		
		for (SceneObject obj : scene.Things) {
			ISect inter = obj.Intersect(ray);
			if (inter != null) {
				if(min_dist > inter.Dist) {
					min_dist = inter.Dist;
					minimum = inter;
				}
			}
		}
		
		return minimum;
	}

	private double TestRay(Ray ray, Scene scene) {
		ISect isect = Intersections(ray, scene);
		if (isect == null)
			return 0;
		return isect.Dist;
	}

	private Color TraceRay(Ray ray, Scene scene, int depth) {
		ISect isect = Intersections(ray, scene);
		if (isect == null)
			return Color.Black;
		return Shade(isect, scene, depth);
	}

	private Color GetNaturalColor(SceneObject thing, Vector pos, Vector norm,
			Vector rd, Scene scene) {
		Color ret = Color.Black;
		for (Light light : scene.Lights) {
			Vector ldis = Vector.Minus(light.Pos, pos);
			Vector livec = Vector.Norm(ldis);
			Ray r = new Ray();
			r.Start = pos;
			r.Dir = livec;
			double neatIsect = TestRay(r, scene);
			boolean isInShadow = !((neatIsect > Vector.Mag(ldis)) || (neatIsect == 0));
			if (!isInShadow) {
				double illum = Vector.Dot(livec, norm);
				Color lcolor = illum > 0 ? Color.Times(illum, light.Color)
						: Color.Black;
				double specular = Vector.Dot(livec, Vector.Norm(rd));
				Color scolor = specular > 0 ? Color.Times(
						Math.pow(specular, thing.Surface.Roughness),
						light.Color) : Color.Black;
				ret = Color.Plus(ret, Color.Plus(
						Color.Times(thing.Surface.Diffuse(pos), lcolor),
						Color.Times(thing.Surface.Specular(pos), scolor)));
			}
		}
		return ret;
	}

	private Color GetReflectionColor(SceneObject thing, Vector pos,
			Vector norm, Vector rd, Scene scene, int depth) {
		Ray r = new Ray();
		r.Start = pos;
		r.Dir = rd;
		return Color.Times(thing.Surface.Reflect(pos),
				TraceRay(r, scene, depth + 1));
	}

	private Color Shade(ISect isect, Scene scene, int depth) {
		Vector d = isect.Ray.Dir;
		Vector pos = Vector.Plus(Vector.Times(isect.Dist, isect.Ray.Dir),
				isect.Ray.Start);
		Vector normal = isect.Thing.Normal(pos);
		Vector reflectDir = Vector.Minus(d,
				Vector.Times(2 * Vector.Dot(normal, d), normal));
		Color ret = Color.Black;
		ret = Color.Plus(ret,
				GetNaturalColor(isect.Thing, pos, normal, reflectDir, scene));

		if (depth >= MaxDepth) {
			return Color.Plus(ret, new Color(.5, .5, .5));
		}

		return Color.Plus(
				ret,
				GetReflectionColor(isect.Thing,
						Vector.Plus(pos, Vector.Times(.001, reflectDir)),
						normal, reflectDir, scene, depth));
	}

	private double RecenterX(double x) {
		return (x - (screenWidth / 2.0)) / (2.0 * screenWidth);
	}

	private double RecenterY(double y) {
		return -(y - (screenHeight / 2.0)) / (2.0 * screenHeight);
	}

	private Vector GetPoint(double x, double y, Camera camera) {
		return Vector.Norm(Vector.Plus(camera.Forward, Vector.Plus(Vector.Times(RecenterX(x), camera.Right),
						Vector.Times(RecenterY(y), camera.Up))));
	}

	@Override
	public void run() {
		int i = getGlobalId();
		int x = i % screenWidth;
		int y = i / screenWidth;
		Ray r = new Ray();
		r.Start = scene.Camera.Pos;
		r.Dir = GetPoint(x, y, scene.Camera);
		Color color = TraceRay(r, scene, 0);
		image[i] = color.GetPacked();
	}

	private Scene DefaultScene() {
		Scene result = new Scene();

		result.Things = new SceneObject[] {
				new Plane(new Vector(0, 1, 0), 0, CheckerBoard.getInstance()),
				new Sphere(new Vector(0, 1, 0), 1, Shiny.getInstance()),
				new Sphere(new Vector(-1, .5, 1.5), .5, Shiny.getInstance()) };

		result.Lights = new Light[] {
				new Light(new Vector(-2, 2.5, 0), new Color(.49, .07, .07)),
				new Light(new Vector(1.5, 2.5, 1.5), new Color(.07, .07, .49)),
				new Light(new Vector(1.5, 2.5, -1.5), new Color(.07, .49, .071)),
				new Light(new Vector(0, 3.5, 0), new Color(.21, .21, .35)) };

		result.Camera = Camera.Create(new Vector(3, 2, 4), new Vector(-1, .5, 0));

		return result;
	}
}
