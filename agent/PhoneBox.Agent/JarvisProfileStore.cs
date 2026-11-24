using System.Text.Json;

namespace PhoneBox.Agent;

internal sealed record JarvisProfile(string AssistantName, string Personality, string Model, string Voice = "slt")
{
    public static readonly JarvisProfile Default = new("JARVIS", "", "qwen2.5-0.5b");

    [System.Text.Json.Serialization.JsonIgnore]
    public bool Valid => !string.IsNullOrWhiteSpace(AssistantName) && AssistantName.Length <= 40
        && Personality is { Length: <= 2000 }
        && Model is ("qwen2.5-0.5b" or "lfm2.5-vl-450m") && Voice is ("slt" or "rms" or "awb");
}

internal sealed class JarvisProfileStore(string directory)
{
    private readonly string path = Path.Combine(directory, "jarvis-profile.json");
    private readonly object gate = new();

    public JarvisProfile Read()
    {
        lock (gate)
        {
            if (!File.Exists(path)) return JarvisProfile.Default;
            var profile = JsonSerializer.Deserialize<JarvisProfile>(File.ReadAllText(path), JsonSerializerOptions.Web);
            if (profile is not { Valid: true }) throw new InvalidDataException("Invalid saved assistant profile.");
            return profile;
        }
    }

    public JarvisProfile Save(JarvisProfile profile)
    {
        if (!profile.Valid) throw new ArgumentException("Invalid assistant name, instructions, model or voice.");
        profile = profile with { AssistantName = profile.AssistantName.Trim(), Personality = profile.Personality.Trim() };
        lock (gate)
        {
            Directory.CreateDirectory(directory);
            var temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
            try
            {
                AppPaths.WriteAllTextPrivate(temp, JsonSerializer.Serialize(profile, JsonSerializerOptions.Web));
                File.Move(temp, path, overwrite: true);
            }
            finally { File.Delete(temp); }
        }
        return profile;
    }
}
