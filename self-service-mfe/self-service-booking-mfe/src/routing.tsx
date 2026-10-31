import BookingComponent from './components/BookingComponent/BookingComponent'
import EnterDetails from './components/EnterDetails/EnterDetails'
import HomePage from './components/HomePage/HomePage'
import HostPreferences from './components/HostPreferences/HostPreferences'
import LoginPage from './components/LoginPage/LoginPage'

function Routing() {
  switch (window.location.pathname) {
    case '/HomePage':
      return <HomePage />
    case '/Booking':
      return <BookingComponent />
    case '/LoginPage':
      return <LoginPage />
    case '/hostPreferences':
      return <HostPreferences />
    case '/EnterDetails':
      return <EnterDetails />
    default:
      return <HomePage />
  }
}

export default Routing