package no.bibsys.handler.requests.events;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.Paths;
import no.bibsys.handler.requests.buildevents.BuildEventBuilder;
import no.bibsys.handler.requests.buildevents.CodePipelineEvent;
import no.bibsys.utils.IoUtils;
import org.junit.Test;

public class BuildEventBuilderTest {


    @Test
    public void create_CodePipelineEvent_CodePipelineEvent() throws IOException {
        String input = IoUtils
            .resourceAsString(Paths.get("events", "mock_codePipeline_event.json"));
        CodePipelineEvent event = (CodePipelineEvent) BuildEventBuilder.create(input);
        assertThat(event.getId(), is(equalTo("a0a4b321-beb6-4da6")));
    }

}
