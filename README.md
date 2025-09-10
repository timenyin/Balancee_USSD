# Balanceè USSD Service Menu Documentation

Submitted to Africa’s Talking  
**Shortcode:** `*XXX*919#`

---

## 1. USSD Service Overview
**Target Users:** Nigerian vehicle owners (with a focus on low-literacy users)  

**Key Features**
- 🚨 SOS repair and towing services  
- 🗣️ Voice-first interaction in **Pidgin, Yoruba, or Hausa**  
- 📱 Simplified menu structure (**maximum 4 options per screen**)  

---

## 2. Complete USSD Menu Walkthrough

### Main Menu (Level 1)
**Voice Prompt**  
Welcome to Balanceè. Press:

For SOS or repairs

For money matters

For product purchase and delivery

To talk to an Agent

yaml
Copy code

---

### Pathway 1: SOS or Repairs (Option 1)
**Voice Prompt**  
Press:

For SOS help

To find a mechanic near you

markdown
Copy code

#### 1.1 SOS Help Flow
**Step-by-Step User Flow**
1. **Emergency Initiation**  
   - Voice: "Press 1 for SOS towing help."  
2. **Location Capture**  
   - User says: “Third Mainland Bridge near petrol station.”  
   - System:
     - Converts voice → text  
     - Maps to GPS coordinates  
     - Matches 200+ towing partners  
3. **Service Matching**
   - Finds nearest tow trucks (≤5 km radius)  
   - Base cost: ₦5,000 (Lagos metro)  
   - +₦500/km after 10 km  
   - Selects cheapest option  
4. **Approval & Payment**
   - Voice: "SOS towing costs ₦X. Press:
     1. Approve & pay 50% deposit
     2. Cancel"  
   - Deposit deducted from wallet  
   - SMS receipt sent  
5. **Confirmation & Tracking**
   - SMS: "SOS confirmed! Tunde (080X XXX) arriving in ~15 mins. Truck No: LAG-1234. Track: [URL]"  

#### 1.2 Find a Mechanic Flow
- User enters current location or landmark  
- System suggests **3 closest mechanics**  
- Displays proximity info  
- Sends SMS with details  

---

### Pathway 2: Money & Payments (Option 2)
**Voice Prompt**  
Press:

To check balance

To add money

yaml
Copy code

#### 2.1 Check Balance
- Option 1: Wallet Balance  
- Option 2: Credit Balance  

#### 2.2 Add Money
- Option 1: Add to Wallet → SMS payment link  
- Option 2: Add to Credit → SMS payment link  

---

### Pathway 3: Product Purchase (Option 3)
**Voice Prompt**  
Product service:

Fuel

Car accessories

Spare parts

Back

yaml
Copy code

#### Example: Fuel Purchase
1. Select station (Mobil, Total, NNPC)  
2. Select fuel type (Petrol, Diesel, Gas)  
3. Enter amount in ₦  
4. Approve payment  
5. Choose delivery or pickup  

#### Example: Car Accessories
- Select item (e.g., car mats ₦8,000)  
- Enter quantity  
- Approve payment  
- Delivery or pickup  

---

### Pathway 4: Talk to an Agent (Option 4)
- User selects Option 4 → call initiated  
- Airtime deducted ₦7.50 per 20 seconds  
- Automatic end if airtime < ₦7.50 or after 10 minutes  
- SMS receipt sent after call  

---

## 3. Technical Specifications
- ⏱️ **Session Timeout:** 90 seconds  
- 🌍 **Languages:** Pidgin, Yoruba, Hausa  
- 📡 **Location Services:** Voice landmark + SMS confirmation  
- 💳 **Payment Integration:** USSD banking & agent networks  

---

## 4. cURL Commands for Testing

After starting your server with:

```bash
.\mvnw.cmd spring-boot:run
Use these commands in your terminal to simulate USSD flows:

Start – Main Menu
bash
Copy code
curl -X POST http://localhost:8080/ussd/callback \
  -d "sessionId=123" \
  -d "phoneNumber=+2348000000000" \
  -d "text="
Pathway 1.1 – SOS Help
bash
Copy code
# Main Menu -> SOS/Repairs
curl -X POST http://localhost:8080/ussd/callback -d "sessionId=123" -d "phoneNumber=+2348000000000" -d "text=1"

# -> SOS Help
curl -X POST http://localhost:8080/ussd/callback -d "sessionId=123" -d "phoneNumber=+2348000000000" -d "text=1*1"

# Enter location
curl -X POST http://localhost:8080/ussd/callback -d "sessionId=123" -d "phoneNumber=+2348000000000" -d "text=1*1*Lagos"

# Approve and pay deposit
curl -X POST http://localhost:8080/ussd/callback -d "sessionId=123" -d "phoneNumber=+2348000000000" -d "text=1*1*Lagos*1"

# Cancel request
curl -X POST http://localhost:8080/ussd/callback -d "sessionId=123" -d "phoneNumber=+2348000000000" -d "text=1*1*Lagos*2"
(Repeat for other flows: Find Mechanic, Money Matters, Product Purchase, Talk to Agent — full list included in your document.)

5. Navigation Tips
Always use the same sessionId for a single flow

The text parameter builds step-by-step (1 → 1*2 → 1*2*Ojota → ...)

Press 0 at any time to go back

Each command shows the next menu options
