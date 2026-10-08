precision highp float;
varying vec2 v;
uniform sampler2D tex;
uniform vec2 outputSize;
// Positive-weight interpolation restricted to fractional pixel edges; no halo.
void main() {
    vec2 size=vec2(240.0,160.0);
    vec2 pixel=v*size;
    vec2 scale=max(floor(outputSize/size),vec2(1.0));
    vec2 fraction=fract(pixel)-0.5;
    vec2 range=0.5-0.5/scale;
    vec2 offset=(fraction-clamp(fraction,-range,range))*scale;
    gl_FragColor=vec4(texture2D(tex,(floor(pixel)+0.5+offset)/size).rgb,1.0);
}
