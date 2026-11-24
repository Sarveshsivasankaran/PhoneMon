using PhoneBox.Agent;
using Xunit;

public sealed class JarvisProfileStoreTests
{
    [Fact]
    public void LegacyProfileGetsDefaultVoiceAndKeepsSelectedVoiceAfterRestart()
    {
        var directory = Directory.CreateTempSubdirectory("jarvis-profile-");
        try
        {
            var store = new JarvisProfileStore(directory.FullName);
            Assert.Equal(JarvisProfile.Default, store.Read());
            File.WriteAllText(Path.Combine(directory.FullName, "jarvis-profile.json"),
                """{"assistantName":"Ada","personality":"Brief","model":"lfm2.5-vl-450m"}""");
            var legacy = store.Read();
            Assert.Equal(new JarvisProfile("Ada", "Brief", "lfm2.5-vl-450m", "slt"), legacy);
            var saved = store.Save(legacy with { Voice = "awb" });
            Assert.Throws<ArgumentException>(() => store.Save(legacy with { Voice = "unknown" }));
            Assert.Equal(saved, new JarvisProfileStore(directory.FullName).Read());
        }
        finally { directory.Delete(recursive: true); }
    }
}
