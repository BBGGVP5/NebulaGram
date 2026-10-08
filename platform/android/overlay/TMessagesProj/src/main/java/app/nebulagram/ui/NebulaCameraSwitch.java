package app.nebulagram.ui;

/** Adds switch blur to the actual legacy encoder while retaining its sampling/mask pipeline. */
public final class NebulaCameraSwitch {
    private NebulaCameraSwitch() { }
    public static String shader(String nativeShader) {
        String shader = nativeShader.replaceAll("texture2D\\s*\\(\\s*sTexture\\s*,", "nebulaSample(");
        int main = shader.indexOf("void main()");
        if (main < 0 || shader.equals(nativeShader)) return nativeShader;
        String sample = "uniform float nebulaBlur;\n"
            + "uniform vec2 nebulaTexel;\n"
            + "vec4 nebulaSample(vec2 uv) {\n"
            + " if (nebulaBlur < 0.001) return texture2D(sTexture, uv);\n"
            + " vec2 d = nebulaTexel * (nebulaBlur * 12.0);\n"
            + " vec4 c = texture2D(sTexture, uv) * 0.25;\n"
            + " c += texture2D(sTexture, uv + vec2(d.x, 0.0)) * 0.125;\n"
            + " c += texture2D(sTexture, uv - vec2(d.x, 0.0)) * 0.125;\n"
            + " c += texture2D(sTexture, uv + vec2(0.0, d.y)) * 0.125;\n"
            + " c += texture2D(sTexture, uv - vec2(0.0, d.y)) * 0.125;\n"
            + " c += texture2D(sTexture, uv + d) * 0.0625;\n"
            + " c += texture2D(sTexture, uv - d) * 0.0625;\n"
            + " c += texture2D(sTexture, uv + vec2(d.x, -d.y)) * 0.0625;\n"
            + " c += texture2D(sTexture, uv + vec2(-d.x, d.y)) * 0.0625;\n"
            + " return c;\n}\n";
        return shader.substring(0, main) + sample + shader.substring(main);
    }
}
