// Public-domain community GBA correction; attribution/assumptions in ADR-012.
precision highp float;
varying vec2 v;
uniform sampler2D tex;
void main() {
    vec3 linear=pow(texture2D(tex,v).rgb,vec3(3.2))*0.94;
    mat3 gamut=mat3(0.82,0.125,0.195, 0.24,0.665,0.075, -0.06,0.21,0.73);
    gl_FragColor=vec4(pow(clamp(gamut*linear,0.0,1.0),vec3(1.0/2.2)),1.0);
}
