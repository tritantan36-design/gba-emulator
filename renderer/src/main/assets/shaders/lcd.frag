precision highp float;
varying vec2 v;
uniform sampler2D tex;
uniform vec2 outputSize;
void main() {
    vec3 c=texture2D(tex,v).rgb;
    vec3 neighbours=(texture2D(tex,v+vec2(1.0/240.0,0.0)).rgb+
                     texture2D(tex,v-vec2(1.0/240.0,0.0)).rgb)*0.5;
    c=mix(c,neighbours,0.04);
    vec2 pixel=fract(v*vec2(240.0,160.0));
    // Fade grid below 2x to avoid undersampled flicker.
    float strength=clamp(min(outputSize.x/240.0,outputSize.y/160.0)-1.0,0.0,1.0);
    float grid=max(step(0.88,pixel.x),step(0.88,pixel.y));
    gl_FragColor=vec4(c*(1.0-0.06*strength*grid),1.0);
}
