package dev.kavrin.balance.operation.web;

import dev.kavrin.balance.account.application.GetBalanceHandler;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.web.configuration.HttpAdapterConfiguration;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
@Import(HttpAdapterConfiguration.class)
class BalanceControllerAvailabilityTests {
    private static final String ACCOUNT = "00000000-0000-0000-0000-000000000001";
    @Autowired MockMvc http;
    @MockitoBean GetBalanceHandler balances;

    @Test
    void balanceLookupRemainsAvailableWhenMutationHandlersAreAbsent() throws Exception {
        when(balances.handle(any())).thenReturn(new Money(150, Currency.getInstance("IRR")));
        http.perform(get("/api/v1/accounts/" + ACCOUNT + "/balance"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balanceRial").value(150));
    }

    @Test
    void disabledMutationsReturnUnavailableWithoutCallingTheBalanceHandler() throws Exception {
        var body = "{\"transactionId\":\"disabled-001\",\"amountRial\":100,\"currency\":\"IRR\"}";
        for (var route : List.of("/accounts/" + ACCOUNT + "/credits", "/accounts/" + ACCOUNT + "/debits", "/transfers")) {
            var payload = route.equals("/transfers") ? body.replace("}", ",\"sourceAccountId\":\"" + ACCOUNT
                    + "\",\"destinationAccountId\":\"00000000-0000-0000-0000-000000000002\"}") : body;
            http.perform(post("/api/v1" + route).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("OPERATION_UNAVAILABLE"));
        }
        verifyNoInteractions(balances);
    }
}
