// Sakuro bilateral denoise — part of the Sakuro project (GPL-3.0).
//
// Edge-preserving suppression of noise and blockiness driven by a single
// intensity tunable. Hooks the RGB frame, so it fits any content class and
// runs identically on both engines (Media3 user-shader runtime and libmpv).

//!PARAM intensity
//!DESC Denoise intensity
//!TYPE float
//!MINIMUM 0.0
//!MAXIMUM 1.0
0.35

//!HOOK MAIN
//!BIND HOOKED
//!DESC Sakuro Denoise (bilateral)

vec4 hook() {
    vec3 center = HOOKED_tex(HOOKED_pos).rgb;
    // Range sharpness relaxes as intensity grows: a strong denoise tolerates
    // larger color steps before treating them as edges to preserve.
    float range = mix(120.0, 20.0, intensity);
    vec3 acc = center;
    float total = 1.0;
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            if (x == 0 && y == 0) continue;
            vec2 off = vec2(float(x), float(y));
            vec3 px = HOOKED_texOff(off).rgb;
            vec3 d = px - center;
            float spatial = exp(-dot(off, off) * 0.25);
            float w = spatial * exp(-dot(d, d) * range);
            acc += px * w;
            total += w;
        }
    }
    return vec4(mix(center, acc / total, min(intensity * 1.5, 1.0)), 1.0);
}
