package text3d;

public class Triangle {
    Point3D p1, p2, p3, normal;
    Triangle(Point3D p1, Point3D p2, Point3D p3, Point3D normal) {
        this.p1 = p1; this.p2 = p2; this.p3 = p3; this.normal = normal;
    }
}